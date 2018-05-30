package org.fenixedu.messaging.core.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.fenixedu.bennu.core.i18n.BundleUtil;
import org.fenixedu.commons.i18n.I18N;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;

import com.google.common.base.Joiner;

public class MessageStoragePolicy implements Serializable {
    private static final long serialVersionUID = 1535994777149570075L;
    private static final String BUNDLE = "MessagingResources", SERIALIZATION_SEPARATOR = "\\s*,\\s*", ALL_PREFIX = "A",
            NONE_PREFIX = "N", AMOUNT_PREFIX = "Q", PERIOD_PREFIX = "P";
    private static final Joiner PRESENTATION_JOINER = Joiner.on(" "), SERIALIZATION_JOINER = Joiner.on(",");
    private static final MessageStoragePolicy ALL = new MessageStoragePolicy(null, null), NONE =
            new MessageStoragePolicy(0, null);
    private static final Map<String, MessageStoragePolicy> POLICIES = new ConcurrentHashMap<String, MessageStoragePolicy>();

    static {
        POLICIES.put(ALL_PREFIX, ALL);
        POLICIES.put(NONE_PREFIX, NONE);
    }

    private final Period period;
    private final Integer amount;

    protected MessageStoragePolicy(final Integer amount, final Period period) {
        this.period = period;
        if(amount != null && amount < 0) {
            throw new IllegalArgumentException("Message storage policy amount cannot be negative.");
        }
        this.amount = amount;
    }

    public static MessageStoragePolicy keep(final Integer amount, final Period period) {
        final String serialization = serialize(amount, period);
        MessageStoragePolicy policy = POLICIES.get(serialization);
        if (policy != null) {
            return policy;
        }
        policy = new MessageStoragePolicy(amount, period);
        POLICIES.put(serialization, policy);
        return policy;
    }

    public static MessageStoragePolicy keep(final Period period) {
        return keep(null, period);
    }

    public static MessageStoragePolicy keep(final Integer amount) {
        return keep(amount, null);
    }

    public static MessageStoragePolicy keepAll() {
        return ALL;
    }

    public static MessageStoragePolicy keepNone() {
        return NONE;
    }

    public Integer getAmount() {
        return amount;
    }

    public Period getPeriod() {
        return period;
    }

    public boolean isKeepAll() {
        return isKeepAll(amount, period);
    }

    public boolean isKeepNone() {
        return isKeepNone(amount);
    }

    protected static boolean isKeepAll(final Integer amount, final Period period) {
        return amount == null && period == null;
    }

    protected static boolean isKeepNone(final Integer amount) {
        return amount != null && amount == 0;
    }

    protected void pruneMessages(final Sender sender) {
        final Set<Message> sent = sender.getMessageSet().stream().filter(message -> message.getSent() != null).collect(Collectors.toSet());
        if (!isKeepAll()) {
            if (!isKeepNone()) {
                Stream<Message> keep = sent.stream();
                if (period != null) {
                    final DateTime cut = DateTime.now().minus(period);
                    keep = keep.filter(message -> message.getCreated().isAfter(cut));
                }
                if (amount != null) {
                    keep = keep.sorted().limit(amount);
                }
                sent.removeAll(keep.collect(Collectors.toSet()));
            }
            sent.forEach(Message::delete);
        }
    }

    public static MessageStoragePolicy internalize(final String serialization) {
        final String[] attrs = serialization.split(SERIALIZATION_SEPARATOR);
        Integer amount = null;
        Period period = null;
        for (final String attr : attrs) {
            switch (attr.substring(0, 1)) {
                case AMOUNT_PREFIX:
                    amount = Integer.valueOf(attr.substring(1));
                    break;
                case PERIOD_PREFIX:
                    period = Period.parse(attr);
                    break;
                case ALL_PREFIX:
                    return keepAll();
                case NONE_PREFIX:
                    return keepNone();
                default:
                    break;
            }
        }
        return keep(amount, period);
    }

    public static MessageStoragePolicy internalize(final String... parts) {
        return internalize(SERIALIZATION_JOINER.join(parts));
    }

    public String serialize() {
        return serialize(amount, period);
    }

    protected static String serialize(final Integer amount, final Period period) {
        if (isKeepAll(amount, period)) {
            return ALL_PREFIX;
        }
        if (isKeepNone(amount)) {
            return NONE_PREFIX;
        }
        final List<String> parts = new ArrayList<>();
        if (period != null) {
            parts.add(period.toString());
        }
        if (amount != null) {
            parts.add(AMOUNT_PREFIX + amount);
        }
        return SERIALIZATION_JOINER.join(parts);
    }

    @Override
    public String toString() {
        final String action = BundleUtil.getString(BUNDLE, "name.storage.policy");
        if (isKeepAll()) {
            return PRESENTATION_JOINER.join(action, BundleUtil.getString(BUNDLE, "name.storage.policy.all"));
        }
        if (isKeepNone()) {
            return PRESENTATION_JOINER.join(action, BundleUtil.getString(BUNDLE, "name.storage.policy.none"));
        }
        final List<String> parts = new ArrayList<>();
        parts.add(action);
        if (amount != null) {
            parts.add(BundleUtil.getString(BUNDLE, "name.storage.policy.amount", Integer.toString(amount)));
        }
        if (period != null) {
            parts.add(BundleUtil
                    .getString(BUNDLE, "name.storage.policy.period", PeriodFormat.wordBased(I18N.getLocale()).print(period)));
        }
        return PRESENTATION_JOINER.join(parts);
    }
}
