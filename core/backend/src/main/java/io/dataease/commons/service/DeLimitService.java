package io.dataease.commons.service;

import java.util.concurrent.TimeUnit;

public interface DeLimitService {

    Boolean checkRestricted(String key, long max, long timeout, TimeUnit timeUnit);
}
