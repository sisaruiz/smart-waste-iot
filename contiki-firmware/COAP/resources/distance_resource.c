/* distance_resource.c - Bin fill level detection (ultrasonic sensor) */

#include "contiki.h"
#include "coap-engine.h"
#include <string.h>
#include <stdbool.h>

#define FULL_THRESHOLD_CM 10  // distance less or equal means bin is full

static bool bin_full_flag = false;  // Flag indicating if bin is full (exposed externally)
static int distance_cm = 15;         // Current simulated distance

static void res_get_handler(coap_message_t *request, coap_message_t *response,
                            uint8_t *buffer, uint16_t preferred_size, int32_t *offset);

static void res_put_handler(coap_message_t *request, coap_message_t *response,
                            uint8_t *buffer, uint16_t preferred_size, int32_t *offset);

RESOURCE(res_distance,
         "title=\"distance sensor\";rt=\"FillLevel\"",
         res_get_handler,
         NULL,
         res_put_handler,
         NULL);

// Expose the flag externally for actuator use
bool get_bin_full_flag() {
  return bin_full_flag;
}

void set_distance(int dist) {
  distance_cm = dist;
  bin_full_flag = (distance_cm <= FULL_THRESHOLD_CM);
}

static void
res_get_handler(coap_message_t *request, coap_message_t *response,
                uint8_t *buffer, uint16_t preferred_size, int32_t *offset) {
  int len = snprintf((char *)buffer, preferred_size,
                     "{\"e\":[{\"n\":\"distance\",\"v\":%d,\"u\":\"cm\"}]}", distance_cm);

  coap_set_header_content_format(response, APPLICATION_JSON);
  coap_set_payload(response, buffer, len);
}

static void
res_put_handler(coap_message_t *request, coap_message_t *response,
                uint8_t *buffer, uint16_t preferred_size, int32_t *offset) {
  const uint8_t *payload = NULL;
  size_t len = coap_get_payload(request, &payload);

  if(len > 0 && payload != NULL) {
    // Expecting JSON payload like {"distance": 8}
    // For simplicity, parse manually
    int new_distance = -1;
    if(sscanf((const char *)payload, "{\"distance\":%d}", &new_distance) == 1) {
      set_distance(new_distance);
      coap_set_status_code(response, CHANGED_2_04);
    } else {
      coap_set_status_code(response, BAD_REQUEST_4_00);
    }
  } else {
    coap_set_status_code(response, BAD_REQUEST_4_00);
  }
}
