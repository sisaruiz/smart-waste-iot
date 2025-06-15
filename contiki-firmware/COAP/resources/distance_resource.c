/* distance_resource.c - Bin fill level detection (e.g. via ultrasonic sensor) */
#include "contiki.h"
#include "coap-engine.h"
#include "dev/leds.h"
#include "json_util.h"
#include <string.h>

static void res_get_handler(coap_message_t *request, coap_message_t *response,
                            uint8_t *buffer, uint16_t preferred_size, int32_t *offset);

RESOURCE(res_distance,
         "title=\"distance sensor\";rt=\"FillLevel\"",
         res_get_handler,
         NULL,
         NULL,
         NULL);

static void res_get_handler(coap_message_t *request, coap_message_t *response,
                            uint8_t *buffer, uint16_t preferred_size, int32_t *offset) {
  // Placeholder value: simulate distance
  int distance_cm = 15; // Example: if <20cm, bin is full
  int len = snprintf((char *)buffer, preferred_size,
                     "{\"e\":[{\"n\":\"distance\",\"v\":%d,\"u\":\"cm\"}]}", distance_cm);
  coap_set_header_content_format(response, APPLICATION_JSON);
  coap_set_payload(response, buffer, len);
}
