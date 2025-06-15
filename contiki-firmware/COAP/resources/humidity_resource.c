/* humidity_resource.c - Optional environmental info */
#include "contiki.h"
#include "coap-engine.h"
#include "dev/leds.h"
#include "json_util.h"
#include <string.h>

static void res_get_handler(coap_message_t *request, coap_message_t *response,
                            uint8_t *buffer, uint16_t preferred_size, int32_t *offset);

RESOURCE(res_humidity,
         "title=\"humidity sensor\";rt=\"Humidity\"",
         res_get_handler,
         NULL,
         NULL,
         NULL);

static void res_get_handler(coap_message_t *request, coap_message_t *response,
                            uint8_t *buffer, uint16_t preferred_size, int32_t *offset) {
  int humidity = 85; // %
  int len = snprintf((char *)buffer, preferred_size,
                     "{\"e\":[{\"n\":\"humidity\",\"v\":%d,\"u\":\"%%RH\"}]}", humidity);
  coap_set_header_content_format(response, APPLICATION_JSON);
  coap_set_payload(response, buffer, len);
}
