#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "contiki.h"
#include "coap-engine.h"
#include "dev/leds.h"
#include "os/dev/button-hal.h"
#include "coap-blocking-api.h"
#include "sys/log.h"
#include "resources/util.h"  // If you need JSON helpers etc.

#define LOG_MODULE "EmptyActuator"
#define LOG_LEVEL LOG_LEVEL_APP

#define SERVER_EP "coap://[fd00::1]:5683"
static char *service_url = "/registration";
static coap_endpoint_t server_ep;
static coap_message_t request[1];
static bool registered = false;

extern coap_resource_t button_resource;
extern volatile int anomaly_type;  // Access shared anomaly state

PROCESS(empty_actuator_process, "Empty Actuator Process");
AUTOSTART_PROCESSES(&empty_actuator_process);

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

PROCESS_THREAD(empty_actuator_process, ev, data) {
  static button_hal_button_t *btn;

  PROCESS_BEGIN();

  leds_off(LEDS_RED | LEDS_GREEN | LEDS_YELLOW);

  while(!registered) {
    coap_endpoint_parse(SERVER_EP, strlen(SERVER_EP), &server_ep);
    coap_init_message(request, COAP_TYPE_CON, COAP_POST, 0);
    coap_set_header_uri_path(request, service_url);
    const char msg[] = "{\"type\":\"empty-actuator\"}";
    coap_set_payload(request, (uint8_t *)msg, sizeof(msg) - 1);
    COAP_BLOCKING_REQUEST(&server_ep, request, client_chunk_handler);
  }

  LOG_INFO("Empty actuator started\n");

  coap_activate_resource(&button_resource, "button");

  btn = button_hal_get_by_index(0);
  if(btn) {
    printf("Button description: %s\n", BUTTON_HAL_GET_DESCRIPTION(btn));
  }

  while(1) {
    PROCESS_WAIT_EVENT();

    if(ev == button_hal_press_event && data == btn) {
      LOG_INFO("Button pressed: resetting anomalies and LEDs\n");
      
      // Reset anomaly state
      anomaly_type = 0;

      // Turn off LEDs
      leds_off(LEDS_RED | LEDS_YELLOW);

      // Trigger button resource (if you want to notify clients or server)
      button_resource.trigger();
    }
  }

  PROCESS_END();
}
