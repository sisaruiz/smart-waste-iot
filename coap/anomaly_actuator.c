#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "contiki.h"
#include "coap-engine.h"
#include "dev/leds.h"
#include "coap-blocking-api.h"
#include "sys/log.h"
#include "resources/util.h"

#define LOG_MODULE "AnomalyActuator"
#define LOG_LEVEL LOG_LEVEL_APP

#define SERVER_EP "coap://[fd00::1]:5683"
static char *service_url = "/registration";
static coap_endpoint_t server_ep;
static coap_message_t request[1];
static bool registered = false;

// Shared anomaly flags
#define ANOMALY_NONE 0
#define ANOMALY_FIRE 1
#define ANOMALY_LEAKAGE 2

volatile int anomaly_type = ANOMALY_NONE;

// Manual override from CLI
static bool manual_override = false;
static bool manual_state = false;

// Extern sensor resources (registered here)
extern coap_resource_t temperature_resource;
extern coap_resource_t humidity_resource;

// ==== CoAP PUT Handler for /anomaly ====
static void res_put_handler(coap_message_t *request, coap_message_t *response,
                            uint8_t *buffer, uint16_t preferred_size, int32_t *offset) {

  size_t len = 0;
  const uint8_t *payload = NULL;
  payload = coap_get_payload(request, &len);

  if(payload == NULL || len == 0) {
    coap_set_status_code(response, BAD_REQUEST_4_00);
    return;
  }

  // Copy payload to a safe buffer and null-terminate
  if(len > 64) len = 64;
  char payload_str[65];
  memcpy(payload_str, payload, len);
  payload_str[len] = '\0';

  if(strstr(payload_str, "\"action\":true") != NULL) {
    manual_override = true;
    manual_state = true;
    LOG_INFO("Received CLI request: activate anomaly\n");
  } else if(strstr(payload_str, "\"action\":false") != NULL) {
    manual_override = true;
    manual_state = false;
    LOG_INFO("Received CLI request: deactivate anomaly\n");
  } else {
    coap_set_status_code(response, BAD_REQUEST_4_00);
    return;
  }

  coap_set_status_code(response, CHANGED_2_04);
}

RESOURCE(res_anomaly,
         "title=\"Anomaly actuator\";rt=\"actuator\";methods=\"PUT\"",
         NULL,
         res_put_handler,
         NULL,
         NULL);

// ========== CoAP Registration Handler ==========
void client_chunk_handler(coap_message_t *response) {
  const uint8_t *chunk;
  if(response == NULL) {
    LOG_ERR("Request timed out\n");
    return;
  }
  LOG_INFO("Registration successful\n");
  int len = coap_get_payload(response, &chunk);
  printf("|%.*s\n", len, (char *)chunk);
  registered = true;
}

// ========== Main Process ==========
PROCESS(anomaly_actuator_process, "Anomaly Actuator Process");
AUTOSTART_PROCESSES(&anomaly_actuator_process);

PROCESS_THREAD(anomaly_actuator_process, ev, data) {
  PROCESS_BEGIN();

  leds_off(LEDS_RED | LEDS_YELLOW);

  // Register with server
  while(!registered) {
    coap_endpoint_parse(SERVER_EP, strlen(SERVER_EP), &server_ep);
    coap_init_message(request, COAP_TYPE_CON, COAP_POST, 0);
    coap_set_header_uri_path(request, service_url);
    const char msg[] = "{\"type\":\"anomaly-actuator\"}";
    coap_set_payload(request, (uint8_t *)msg, sizeof(msg) - 1);
    COAP_BLOCKING_REQUEST(&server_ep, request, client_chunk_handler);
  }

  LOG_INFO("Anomaly actuator started\n");

  // Activate sensors and actuator resource
  coap_activate_resource(&temperature_resource, "temperature");
  coap_activate_resource(&humidity_resource, "humidity");
  coap_activate_resource(&res_anomaly, "anomaly");

  while(1) {
    PROCESS_PAUSE(); // Idle loop

    bool fire = (anomaly_type == ANOMALY_FIRE);
    bool leak = (anomaly_type == ANOMALY_LEAKAGE);

    if(manual_override) {
      if(manual_state) {
        // Manual ON (display warning)
        leds_on(LEDS_RED | LEDS_YELLOW);
      } else {
        // Manual OFF (force all LEDs off)
        leds_off(LEDS_RED | LEDS_YELLOW);
      }
    } else {
      // Follow automatic anomaly state
      if(fire) {
        leds_on(LEDS_RED);
        leds_off(LEDS_YELLOW);
      } else if(leak) {
        leds_on(LEDS_YELLOW);
        leds_off(LEDS_RED);
      } else {
        leds_off(LEDS_RED | LEDS_YELLOW);
      }
    }
  }

  PROCESS_END();
}
