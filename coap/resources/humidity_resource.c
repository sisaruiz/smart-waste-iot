#include "contiki.h"
#include "coap-engine.h"
#include "dev/leds.h"
#include "util.h"
#include <string.h>
#include <stdlib.h>

#define HUMIDITY_THRESHOLD 90

extern volatile int anomaly_type;
#define ANOMALY_LEAKAGE 2
#define ANOMALY_NONE 0

static void res_get_handler(coap_message_t *request, coap_message_t *response,
                            uint8_t *buffer, uint16_t preferred_size, int32_t *offset);
static void res_put_handler(coap_message_t *request, coap_message_t *response,
                            uint8_t *buffer, uint16_t preferred_size, int32_t *offset);

RESOURCE(res_humidity,
         "title=\"humidity sensor\";rt=\"Humidity\"",
         res_get_handler,
         NULL,
         res_put_handler,
         NULL);

static void
res_get_handler(coap_message_t *request, coap_message_t *response,
                uint8_t *buffer, uint16_t preferred_size, int32_t *offset) {
  int humidity = 85;
  int len = snprintf((char *)buffer, preferred_size,
                     "{\"e\":[{\"n\":\"humidity\",\"v\":%d,\"u\":\"%%RH\"}]}", humidity);
  coap_set_header_content_format(response, APPLICATION_JSON);
  coap_set_payload(response, buffer, len);
}

static void
res_put_handler(coap_message_t *request, coap_message_t *response,
                uint8_t *buffer, uint16_t preferred_size, int32_t *offset) {
  const uint8_t *payload = NULL;
  size_t len = coap_get_payload(request, &payload);

  if(len > 0 && payload != NULL) {
    char *value = get_json_value_string((const char *)payload, "threshold");
    if(value != NULL) {
      int threshold = atoi(value);
      free(value);

      if(threshold >= HUMIDITY_THRESHOLD) {
        anomaly_type = ANOMALY_LEAKAGE;
      } else {
        anomaly_type = ANOMALY_NONE;
      }

      coap_set_status_code(response, CHANGED_2_04);
    } else {
      coap_set_status_code(response, BAD_REQUEST_4_00);
    }
  } else {
    coap_set_status_code(response, BAD_REQUEST_4_00);
  }
}
