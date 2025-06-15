#include "contiki.h"
#include "net/routing/routing.h"
#include "mqtt.h"
#include "net/ipv6/uip.h"
#include "net/ipv6/uip-icmp6.h"
#include "net/ipv6/sicslowpan.h"
#include "sys/etimer.h"
#include "sys/ctimer.h"
#include "lib/sensors.h"
#include "dev/button-hal.h"
#include "dev/leds.h"
#include "os/sys/log.h"
#include "mqtt-client.h"
#include <string.h>
#include <strings.h>

#define LOG_MODULE "mqtt-client"
#ifdef MQTT_CLIENT_CONF_LOG_LEVEL
#define LOG_LEVEL MQTT_CLIENT_CONF_LOG_LEVEL
#else
#define LOG_LEVEL LOG_LEVEL_DBG
#endif

/* MQTT broker address */
#define MQTT_CLIENT_BROKER_IP_ADDR "fd00::1"
static const char *broker_ip = MQTT_CLIENT_BROKER_IP_ADDR;

/* Default config */
#define DEFAULT_BROKER_PORT 1883
#define DEFAULT_PUBLISH_INTERVAL (30 * CLOCK_SECOND)

/* TCP and IP address size */
#define MAX_TCP_SEGMENT_SIZE 32
#define CONFIG_IP_ADDR_STR_LEN 64

static struct mqtt_message *msg_ptr = NULL;

/* Buffers for Client ID and MQTT topics */
#define BUFFER_SIZE 64
static char client_id[BUFFER_SIZE];
static char pub_topic[BUFFER_SIZE];
static char sub_topic[BUFFER_SIZE];

/* Sensor periodicity */
#define SENSOR_PERIOD 10
static struct ctimer humidity_timer;

/* Application-level buffer */
#define APP_BUFFER_SIZE 512
static char app_buffer[APP_BUFFER_SIZE];

static struct mqtt_connection conn;

mqtt_status_t status;
char broker_address[CONFIG_IP_ADDR_STR_LEN];

/* Timer for MQTT state machine */
#define STATE_MACHINE_PERIODIC (CLOCK_SECOND >> 1)
static struct etimer periodic_timer;

/* MQTT connection states */
static uint8_t state;
#define STATE_INIT          0 // Initial state
#define STATE_NET_OK        1 // Network initialized
#define STATE_CONNECTING    2 // Connecting to broker
#define STATE_CONNECTED     3 // Connected
#define STATE_SUBSCRIBED    4 // Subscribed to topic
#define STATE_DISCONNECTED  5 // Disconnected

PROCESS(mqtt_client_humidity, "MQTT humidity sensor");
AUTOSTART_PROCESSES(&mqtt_client_humidity);

/* Simulated humidity control parameters */
static uint8_t min_humidity = 40;
static uint8_t max_humidity = 60;
static uint8_t start_humidity = 50;
static int humidity = 50;
static bool actuator_on = false;
static bool humidifying = true;
static bool first_sensing = true;

/* Simulate sensing humidity and publish it */
static void
sense_humidity(void)
{
    if(!actuator_on) {
        if(humidifying) {
            humidity += 3;
            if(humidity > max_humidity) {
                actuator_on = true;
            }
        } else {
            humidity -= 3;
            if(humidity < min_humidity) {
                actuator_on = true;
            }
        }
    } else {
        if(humidifying) {
            humidity -= 3;
            if(humidity == start_humidity) {
                actuator_on = false;
                humidifying = !humidifying;
            }
        } else {
            humidity += 3;
            if(humidity == start_humidity) {
                actuator_on = false;
                humidifying = !humidifying;
            }
        }
    }

    sprintf(pub_topic, "humidity");
    sprintf(app_buffer, "{ \"value\": %d }", humidity);

    mqtt_publish(&conn, NULL, pub_topic,
                 (uint8_t *)app_buffer,
                 strlen(app_buffer),
                 MQTT_QOS_LEVEL_0,
                 MQTT_RETAIN_OFF);

    ctimer_set(&humidity_timer, SENSOR_PERIOD * CLOCK_SECOND, sense_humidity, NULL);
}

/* Handler for incoming published messages on subscribed topics */
static void
pub_handler(const char *topic, uint16_t topic_len,
            const uint8_t *chunk, uint16_t chunk_len)
{
    LOG_INFO("Humidity Pub Handler: topic=HUMIDITY, min=%d max=%d\n", min_humidity, max_humidity);
}

/* MQTT event handler */
static void
mqtt_event(struct mqtt_connection *m, mqtt_event_t event, void *data)
{
    switch(event) {
        case MQTT_EVENT_CONNECTED:
            printf("MQTT connected\n");
            state = STATE_CONNECTED;
            break;

        case MQTT_EVENT_DISCONNECTED:
            printf("MQTT disconnected, reason %u\n", *((mqtt_event_t *)data));
            state = STATE_DISCONNECTED;
            process_poll(&mqtt_client_humidity);
            break;

        case MQTT_EVENT_PUBLISH:
            msg_ptr = data;
            pub_handler(msg_ptr->topic, strlen(msg_ptr->topic), msg_ptr->payload_chunk, msg_ptr->payload_length);
            break;

        case MQTT_EVENT_SUBACK:
#if MQTT_311
        {
            mqtt_suback_event_t *suback_event = (mqtt_suback_event_t *)data;
            if(suback_event->success) {
                printf("Subscribed successfully\n");
            } else {
                printf("Subscribe failed (ret code %x)\n", suback_event->return_code);
            }
        }
#else
            printf("Subscribed successfully\n");
#endif
            break;

        case MQTT_EVENT_UNSUBACK:
            printf("Unsubscribed successfully\n");
            break;

        case MQTT_EVENT_PUBACK:
            printf("Publish complete\n");
            break;

        default:
            printf("Unhandled MQTT event: %i\n", event);
            break;
    }
}

/* Check for IPv6 connectivity */
static bool
have_connectivity(void)
{
    return (uip_ds6_get_global(ADDR_PREFERRED) != NULL &&
            uip_ds6_defrt_choose() != NULL);
}

/* Main process thread */
PROCESS_THREAD(mqtt_client_humidity, ev, data)
{
    PROCESS_BEGIN();

    LOG_INFO("Starting humidity MQTT client\n");

    snprintf(client_id, BUFFER_SIZE, "%02x%02x%02x%02x%02x%02x",
             linkaddr_node_addr.u8[0], linkaddr_node_addr.u8[1],
             linkaddr_node_addr.u8[2], linkaddr_node_addr.u8[5],
             linkaddr_node_addr.u8[6], linkaddr_node_addr.u8[7]);

    mqtt_register(&conn, &mqtt_client_humidity, client_id, mqtt_event, MAX_TCP_SEGMENT_SIZE);

    state = STATE_INIT;

    etimer_set(&periodic_timer, STATE_MACHINE_PERIODIC);

    while(1) {
        PROCESS_YIELD();

        if((ev == PROCESS_EVENT_TIMER && data == &periodic_timer) || ev == PROCESS_EVENT_POLL) {
            switch(state) {
                case STATE_INIT:
                    if(have_connectivity()) {
                        state = STATE_NET_OK;
                    }
                    break;

                case STATE_NET_OK:
                    printf("Connecting to MQTT broker...\n");
                    memcpy(broker_address, broker_ip, strlen(broker_ip));
                    mqtt_connect(&conn, broker_address,
                                 DEFAULT_BROKER_PORT,
                                 (DEFAULT_PUBLISH_INTERVAL * 3) / CLOCK_SECOND,
                                 MQTT_CLEAN_SESSION_ON);
                    state = STATE_CONNECTING;
                    break;

                case STATE_CONNECTED:
                    strcpy(sub_topic, "actuator_humidity");
                    status = mqtt_subscribe(&conn, NULL, sub_topic, MQTT_QOS_LEVEL_0);
                    printf("Subscribing to %s\n", sub_topic);
                    if(status == MQTT_STATUS_OUT_QUEUE_FULL) {
                        LOG_ERR("Subscribe command queue full!\n");
                        PROCESS_EXIT();
                    }
                    state = STATE_SUBSCRIBED;
                    break;

                case STATE_SUBSCRIBED:
                    if(first_sensing) {
                        ctimer_set(&humidity_timer, SENSOR_PERIOD * CLOCK_SECOND, sense_humidity, NULL);
                        first_sensing = false;
                    }
                    break;

                case STATE_DISCONNECTED:
                    LOG_ERR("Disconnected from MQTT broker\n");
                    state = STATE_INIT;
                    break;
            }
            etimer_set(&periodic_timer, STATE_MACHINE_PERIODIC);
        }
    }

    PROCESS_END();
}
