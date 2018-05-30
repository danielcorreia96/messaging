package org.fenixedu.messaging.emaildispatch.domain;

import pt.ist.fenixframework.Atomic;
import pt.ist.fenixframework.Atomic.TxMode;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.fenixedu.messaging.core.domain.MessagingSystem;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class EmailBlacklist extends EmailBlacklist_Base {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalEmailMessageDispatchReport.class);

    private static final String TIMESTAMP = "ts";
    private static final String EMAIL = "eml";
    private static final String STATUS = "st";
    private static final String STATUS_INVALID = "invalid";
    private static final String STATUS_FAILED = "failed";

    protected EmailBlacklist() {
        super();
        setMessagingSystem(MessagingSystem.getInstance());
    }

    public static EmailBlacklist getInstance() {
        final EmailBlacklist instance = MessagingSystem.getInstance().getBlacklist();
        return instance == null ? create() : instance;
    }

    @Atomic(mode = TxMode.WRITE)
    private static EmailBlacklist create() {
        final EmailBlacklist instance = MessagingSystem.getInstance().getBlacklist();
        return instance == null ? new EmailBlacklist() : instance;
    }

    @Override
    protected JsonArray getBlacklist() {
        return super.getBlacklist() == null || super.getBlacklist().isJsonNull() ? new JsonArray() : super.getBlacklist().getAsJsonArray();
    }

    public void addInvalidAddress(final String invalid) {
        log(invalid, STATUS_INVALID);
        LOGGER.warn("Blacklisting email {} because is invalid", invalid);
    }

    public void addFailedAddress(final String failed) {
        log(failed, STATUS_FAILED);
        LOGGER.warn("Blacklisting email {} because it failed a deliver", failed);
    }

    public void pruneOldLogs(final DateTime before) {
        final JsonArray newBl = new JsonArray();
        for (final JsonElement log : getBlacklist().getAsJsonArray()) {
            final String timestamp = log.getAsJsonObject().get(TIMESTAMP).getAsString();
            if (!DateTime.parse(timestamp).isBefore(before)) {
                newBl.add(log);
            }
        }
        setBlacklist(newBl);
    }

    public Set<String> getInvalidEmails() {
        final Set<String> invalid = new HashSet<>();
        for (final JsonElement log : getBlacklist().getAsJsonArray()) {
            if (log.getAsJsonObject().get(STATUS).getAsString().equals(STATUS_INVALID)) {
                invalid.add(log.getAsJsonObject().get(EMAIL).getAsString());
            }
        }
        return invalid;
    }

    public Set<String> getFailedEmails(final int times) {
        final Multiset<String> failed = HashMultiset.create();
        for (final JsonElement log : getBlacklist().getAsJsonArray()) {
            if (log.getAsJsonObject().get(STATUS).getAsString().equals(STATUS_FAILED)) {
                failed.add(log.getAsJsonObject().get(EMAIL).getAsString());
            }
        }
        return failed.stream().filter(email -> failed.count(email) > times).collect(Collectors.toSet());
    }

    private void log(final String email, final String status) {
        final JsonObject log = new JsonObject();
        log.addProperty(TIMESTAMP, new DateTime().toString());
        log.addProperty(EMAIL, email);
        log.addProperty(STATUS, status);
        final JsonArray blacklist = getBlacklist();
        blacklist.add(log);
        setBlacklist(blacklist);
    }
}
