/* temperature_resource.c - Fire or overheating detection */
#include "contiki.h"
#include "coap-engine.h"
#include "dev/leds.h"
#include "json_util.h"
#include <string.h>

static void res_get_handler(coap_message_t *request, coap_message_t *response,
                            uint8_t *buffer, uint16_t preferred_size, int32_t *offset);

RESOURCE(res_temperature,
         "title=\"temperature sensor\";rt=\"Temperature\"",
         res_get_handler,
         NULL,
         NULL,
         NULL);

static void res_get_handler(coap_message_t *request, coap_message_t *response,
                            uint8_t *buffer, uint16_t preferred_size, int32_t *offset) {
  int temp = 45; // °C
  int len = snprintf((char *)buffer, preferred_size,
                     "{\"e\":[{\"n\":\"temperature\",\"v\":%d,\"u\":\"Cel\"}]}", temp);
  coap_set_header_content_format(response, APPLICATION_JSON);
  coap_set_payload(response, buffer, len);
}
