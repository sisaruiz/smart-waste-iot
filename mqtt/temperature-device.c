#include "contiki.h"
#include <stdio.h>
#include <stdlib.h>

// Shared variable
int current_temperature = 200; // 20.0°C as initial value

#define TEMP_MIN 150  // 15.0°C
#define TEMP_MAX 400  // 40.0°C
#define TEMP_INCREMENT 2 // = 0.2°C
#define TEMP_TIMER_INTERVAL (15 * CLOCK_SECOND)

PROCESS(temperature_process, "Temperature Device Process");
AUTOSTART_PROCESSES(&temperature_process);

PROCESS_THREAD(temperature_process, ev, data)
{
  static struct etimer temp_timer;

  PROCESS_BEGIN();
  printf("Temperature sensor process started\n");

  etimer_set(&temp_timer, TEMP_TIMER_INTERVAL);

  while(1) {
    PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&temp_timer));

    // Simulate random increase or decrease of temperature
    int change = (rand() % 3) - 1; // -1, 0, or +1
    current_temperature += change * TEMP_INCREMENT;

    // Clamp to range
    if(current_temperature < TEMP_MIN) current_temperature = TEMP_MIN;
    if(current_temperature > TEMP_MAX) current_temperature = TEMP_MAX;

    printf("Simulated Temperature: %d.%d°C\n", current_temperature / 10, current_temperature % 10);

    etimer_reset(&temp_timer);
  }

  PROCESS_END();
}
