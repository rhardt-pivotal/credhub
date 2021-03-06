package org.cloudfoundry.credhub.util;

import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

@Component
public class CurrentTimeProvider implements DateTimeProvider {
  @Override
  public Optional<TemporalAccessor> getNow() {
    return Optional.of(getInstant());
  }

  public Instant getInstant() {
    return Instant.now();
  }

  public long currentTimeMillis() {
    return System.currentTimeMillis();
  }

  public void sleep(long sleepTimeInMillis) throws InterruptedException {
      Thread.sleep(sleepTimeInMillis);
  }
}
