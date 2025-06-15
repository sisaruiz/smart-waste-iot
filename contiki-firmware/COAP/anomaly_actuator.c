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

extern coap_resource_t temperature_resource;
extern coap_resource_t humidity_resource;

void client_chunk_handler(coap_message_t *response) {
  const uint8_t *chunk;
  if(response == NULL) {
    LOG_ERR("Request timed out");
    return;
  }
  LOG_INFO("Registration successful\n");
  int len = coap_get_payload(response, &chunk);
  printf("|%.*s\n", len, (char *)chunk);
  registered = true;
}

PROCESS(anomaly_actuator_process, "Anomaly Actuator Process");
AUTOSTART_PROCESSES(&anomaly_actuator_process);

PROCESS_THREAD(anomaly_actuator_process, ev, data) {
  PROCESS_BEGIN();

  leds_off(LEDS_RED | LEDS_YELLOW);

  while(!registered) {
    coap_endpoint_parse(SERVER_EP, strlen(SERVER_EP), &server_ep);
    coap_init_message(request, COAP_TYPE_CON, COAP_POST, 0);
    coap_set_header_uri_path(request, service_url);
    const char msg[] = "{\"type\":\"anomaly-actuator\"}";
    coap_set_payload(request, (uint8_t *)msg, sizeof(msg) - 1);
    COAP_BLOCKING_REQUEST(&server_ep, request, client_chunk_handler);
  }

  LOG_INFO("Anomaly actuator started\n");

  coap_activate_resource(&temperature_resource, "temperature");
  coap_activate_resource(&humidity_resource, "humidity");

  while(1) {
    PROCESS_PAUSE();  // could also use timers if polling

    if(anomaly_type == ANOMALY_FIRE) {
      leds_on(LEDS_RED);
      leds_off(LEDS_YELLOW);
    } else if(anomaly_type == ANOMALY_LEAKAGE) {
      leds_on(LEDS_YELLOW);
      leds_off(LEDS_RED);
    } else {
      leds_off(LEDS_RED | LEDS_YELLOW);
    }
  }

  PROCESS_END();
}
