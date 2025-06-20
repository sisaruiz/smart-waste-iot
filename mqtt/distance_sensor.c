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

/* MQTT broker address. */
#define MQTT_CLIENT_BROKER_IP_ADDR "fd00::1"
static const char *broker_ip = MQTT_CLIENT_BROKER_IP_ADDR;

// Default config values
#define DEFAULT_BROKER_PORT 1883
#define DEFAULT_PUBLISH_INTERVAL (30 * CLOCK_SECOND)

/* Maximum TCP segment size and length of IPv6 addresses*/
#define MAX_TCP_SEGMENT_SIZE 32
#define CONFIG_IP_ADDR_STR_LEN 64

static struct mqtt_message *msg_ptr = 0;

// Buffers for Client ID and Topics.
#define BUFFER_SIZE 64
static char client_id[BUFFER_SIZE];
static char pub_topic[BUFFER_SIZE];
static char sub_topic[BUFFER_SIZE];

#define SENSOR_PERIOD 10
static struct ctimer distance_timer;

// Application level buffer
#define APP_BUFFER_SIZE 512
static char app_buffer[APP_BUFFER_SIZE];

static struct mqtt_connection conn;

mqtt_status_t status;
char broker_address[CONFIG_IP_ADDR_STR_LEN];

// Periodic timer to check the state of the MQTT client
#define STATE_MACHINE_PERIODIC     (CLOCK_SECOND >> 1)
static struct etimer periodic_timer;

/* Various states */
static uint8_t state;
#define STATE_INIT          0 // Initial state
#define STATE_NET_OK        1 // Network is initialized
#define STATE_CONNECTING    2 // Connecting to MQTT broker
#define STATE_CONNECTED     3 // Connection successful
#define STATE_SUBSCRIBED    4 // Topics of interest subscribed
#define STATE_DISCONNECTED  5 // Disconnected from MQTT broker

PROCESS(mqtt_client_distance, "MQTT sensor distance");
AUTOSTART_PROCESSES(&mqtt_client_distance);

static int distance = 50; // Starting simulated distance (cm)
static bool actuator_on = false;
static bool first_sensing = true;

static void sense_distance() {
    // Simulate distance changes from 0 to 100 cm cyclically
    distance += 7;
    if (distance > 100) {
        distance = 0;
    }

    sprintf(pub_topic, "%s", "distance");
    sprintf(app_buffer , "{ \"value\": %d }", distance);
    mqtt_publish(&conn, NULL, pub_topic,
        (uint8_t*) app_buffer,
        strlen(app_buffer),
        MQTT_QOS_LEVEL_0,
        MQTT_RETAIN_OFF);

    ctimer_set(&distance_timer, SENSOR_PERIOD * CLOCK_SECOND, sense_distance, NULL);
}

static void pub_handler(const char *topic, uint16_t topic_len, const uint8_t *chunk,
            uint16_t chunk_len) {
    LOG_INFO("Distance Pub Handler: topic=%.*s\n", topic_len, topic);
    // TODO: parse chunk (JSON) and handle actuator commands if needed
}

static void mqtt_event(struct mqtt_connection *m, mqtt_event_t event, void *data) {
    switch(event) {
        case MQTT_EVENT_CONNECTED:
            printf("Application has a MQTT connection\n");
            state = STATE_CONNECTED;
            break;
        case MQTT_EVENT_DISCONNECTED:
            printf("MQTT Disconnect. Reason %u\n", *((mqtt_event_t *)data));
            state = STATE_DISCONNECTED;
            process_poll(&mqtt_client_distance);
            break;
        case MQTT_EVENT_PUBLISH:
            msg_ptr = data;
            pub_handler(msg_ptr->topic, strlen(msg_ptr->topic),
                        msg_ptr->payload_chunk, msg_ptr->payload_length);
            break;
        case MQTT_EVENT_SUBACK:
    #if MQTT_311
            mqtt_suback_event_t *suback_event = (mqtt_suback_event_t *)data;
            if(suback_event->success) {
                printf("Subscribed to topic successfully\n");
            } else {
                printf("Failed to subscribe to topic (ret code %x)\n", suback_event->return_code);
            }
    #else
            printf("Subscribed to topic successfully\n");
    #endif
            break;
        case MQTT_EVENT_UNSUBACK:
            printf("Unsubscribed from topic successfully\n");
            break;
        case MQTT_EVENT_PUBACK:
            printf("Publishing complete.\n");
            break;
        default:
            printf("Unhandled MQTT event: %i\n", event);
            break;        
    }
}

static bool have_connectivity(void) {
  if(uip_ds6_get_global(ADDR_PREFERRED) == NULL ||
     uip_ds6_defrt_choose() == NULL) {
    return false;
  }
  return true;
}

PROCESS_THREAD(mqtt_client_distance, ev, data) {
    PROCESS_BEGIN();
    LOG_INFO("Starting distance MQTT Client\n");

    //Initialize the ClientID as MAC address of the node
    snprintf(client_id , BUFFER_SIZE, "%02x%02x%02x%02x%02x%02x",
            linkaddr_node_addr.u8[0], linkaddr_node_addr.u8[1],
            linkaddr_node_addr.u8[2], linkaddr_node_addr.u8[5],
            linkaddr_node_addr.u8[6], linkaddr_node_addr.u8[7]);

    mqtt_register(&conn, &mqtt_client_distance, client_id,
        mqtt_event, MAX_TCP_SEGMENT_SIZE);

    state=STATE_INIT;

    etimer_set (&periodic_timer , STATE_MACHINE_PERIODIC);

    while(1){
        PROCESS_YIELD();

        if((ev == PROCESS_EVENT_TIMER && data == &periodic_timer) || ev == PROCESS_EVENT_POLL){
            if(state == STATE_INIT){
                if(have_connectivity() == true) {
                    state = STATE_NET_OK;
                }
            }

            if(state == STATE_NET_OK){
                printf("Connecting to MQTT broker...\n");
                memcpy(broker_address , broker_ip , strlen(broker_ip));
                mqtt_connect(&conn, broker_address,
                    DEFAULT_BROKER_PORT,
                    (DEFAULT_PUBLISH_INTERVAL * 3) / CLOCK_SECOND,
                    MQTT_CLEAN_SESSION_ON);
                state = STATE_CONNECTING;
            }

            if(state == STATE_CONNECTED){
                strcpy(sub_topic, "actuator_distance");
                status = mqtt_subscribe(&conn, NULL, sub_topic, MQTT_QOS_LEVEL_0);
                printf("Subscribing to topic actuator_distance...\n");
                if(status == MQTT_STATUS_OUT_QUEUE_FULL) {
                    LOG_ERR("Subscribe command queue full!\n");
                    PROCESS_EXIT();
                }
                state = STATE_SUBSCRIBED;
            }

            if(state == STATE_SUBSCRIBED){
                if(first_sensing){
                    ctimer_set(&distance_timer, SENSOR_PERIOD * CLOCK_SECOND, sense_distance, NULL);
                    first_sensing = false;
                }
            }
            else if(state == STATE_DISCONNECTED){
                LOG_ERR("Disconnected from MQTT broker\n");
                state = STATE_INIT;
            }
            etimer_set(&periodic_timer, STATE_MACHINE_PERIODIC);
        }
    }

    PROCESS_END();
}
