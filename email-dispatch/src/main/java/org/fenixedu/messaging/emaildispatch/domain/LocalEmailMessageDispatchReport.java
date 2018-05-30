package org.fenixedu.messaging.emaildispatch.domain;

import org.fenixedu.messaging.core.domain.Sender;
import pt.ist.fenixframework.Atomic;
import pt.ist.fenixframework.Atomic.TxMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.mail.MessagingException;

import org.fenixedu.bennu.core.domain.User;
import org.fenixedu.bennu.core.domain.UserProfile;
import org.fenixedu.bennu.core.groups.Group;
import org.fenixedu.messaging.core.domain.Message;
import org.fenixedu.messaging.core.domain.MessagingSystem;
import org.fenixedu.messaging.emaildispatch.EmailDispatchConfiguration;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

public class LocalEmailMessageDispatchReport extends LocalEmailMessageDispatchReport_Base {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalEmailMessageDispatchReport.class);
    private static final boolean RECIPIENTS_AS_BCCS = EmailDispatchConfiguration.getConfiguration().recipientsAsBccs();

    public LocalEmailMessageDispatchReport(final Collection<MimeMessageHandler> handlers, final Integer validCount, final Integer invalidCount) {
        super();
        getHandlerSet().addAll(handlers);
        setTotalCount(validCount + invalidCount);
        setDeliveredCount(0);
        setFailedCount(0);
        setInvalidCount(invalidCount);
        setQueue(MessagingSystem.getInstance());
    }

    @Override
    public boolean isFinished() {
        return getHandlerSet().isEmpty();
    }

    public void deliver() {
        if (isFinished() && getQueue() != null) {
            finishUpDelivery();
        }
        else {
            for (final MimeMessageHandler handler : getHandlerSet()) {
                try {
                    handler.deliver();
                } catch (MessagingException e) {
                    LOGGER.error("Error sending message", e);
                }
            }
            if (isFinished()) {
                if (!super.isFinished()) {
                    LOGGER.error("Numbers are not right: total {} delivered {} invalid {} failed {}", getTotalCount(),
                            getDeliveredCount(), getInvalidCount(), getFailedCount());
                }
                finishUpDelivery();
            }
        }
    }

    @Atomic(mode = TxMode.WRITE)
    private void finishUpDelivery() {
        setFinishedDelivery(new DateTime());
        setQueue(null);
    }

    public static LocalEmailMessageDispatchReport dispatch(final Message message) {
        final List<String> invalids = new ArrayList<>();
        final EmailBlacklist blacklist = EmailBlacklist.getInstance();
        final Predicate<String> validator = email -> {
            final boolean valid = MessagingSystem.Util.isValidEmail(email);
            if (!valid) {
                invalids.add(email);
            }
            return valid;
        };

        final Set<UserProfile> tos = getProfilesAllowed(message.getSender(), message.getToGroups());
        final Set<UserProfile> ccs = getProfilesAllowed(message.getSender(), message.getCcGroups());
        final Set<UserProfile> bccs = getProfilesAllowed(message.getSender(), message.getBccGroups());

        Map<Locale, Set<String>> tosByLocale;
        Map<Locale, Set<String>> ccsByLocale;
        Map<Locale, Set<String>> bccsByLocale;
        final Locale defLocale = message.getPreferredLocale();
        final Set<Locale> messageLocales = message.getContentLocales();

        if (RECIPIENTS_AS_BCCS) {
            bccs.addAll(tos);
            bccs.addAll(ccs);

            tosByLocale = Maps.newHashMap();
            ccsByLocale = Maps.newHashMap();
            bccsByLocale = emailsByMessageLocale(bccs, validator, defLocale, messageLocales);

            final Set<String> singleTos = message.getSingleTosSet().stream().filter(validator).collect(Collectors.toSet());
            bccsByLocale.computeIfAbsent(message.getPreferredLocale(), locale -> new HashSet<>()).addAll(singleTos);

            final Set<String> singleBccs = message.getSingleBccsSet().stream().filter(validator).collect(Collectors.toSet());
            bccsByLocale.computeIfAbsent(message.getPreferredLocale(), locale -> new HashSet<>()).addAll(singleBccs);
        }
        else {
            //XXX force disjoint recipient lists - priority order: tos > ccs > bccs > single bccs
            ccs.removeAll(tos);
            bccs.removeAll(tos);
            bccs.removeAll(ccs);

            Set<String> singleTos = message.getSingleTosSet();
            tos.stream().map(UserProfile::getEmail).forEach(singleTos::remove);
            ccs.stream().map(UserProfile::getEmail).forEach(singleTos::remove);
            bccs.stream().map(UserProfile::getEmail).forEach(singleTos::remove);

            Set<String> singleBccs = message.getSingleBccsSet();
            tos.stream().map(UserProfile::getEmail).forEach(singleBccs::remove);
            ccs.stream().map(UserProfile::getEmail).forEach(singleBccs::remove);
            bccs.stream().map(UserProfile::getEmail).forEach(singleBccs::remove);

            tosByLocale = emailsByMessageLocale(tos, validator, defLocale, messageLocales);
            ccsByLocale = emailsByMessageLocale(ccs, validator, defLocale, messageLocales);
            bccsByLocale = emailsByMessageLocale(bccs, validator, defLocale, messageLocales);

            singleTos = singleTos.stream().filter(validator).collect(Collectors.toSet());
            tosByLocale.computeIfAbsent(message.getPreferredLocale(), locale -> new HashSet<>()).addAll(singleTos);

            singleBccs = singleBccs.stream().filter(validator).collect(Collectors.toSet());
            bccsByLocale.computeIfAbsent(message.getPreferredLocale(), locale -> new HashSet<>()).addAll(singleBccs);
        }

        final Collection<MimeMessageHandler> handlers = MimeMessageHandler.create(tosByLocale, ccsByLocale, bccsByLocale);
        final int valids =
                Stream.of(tosByLocale, ccsByLocale, bccsByLocale).flatMap(addressesByLocale -> addressesByLocale.values().stream())
                        .mapToInt(Collection::size).sum();

        invalids.forEach(blacklist::addInvalidAddress);

        return new LocalEmailMessageDispatchReport(handlers, valids, invalids.size());
    }

    private static Map<Locale, Set<String>> emailsByMessageLocale(final Set<UserProfile> users, final Predicate<String> emailValidator,
            final Locale defLocale, final Set<Locale> messageLocales) {
        final Map<Locale, Set<String>> emails = new HashMap<>();
        users.stream().filter(userProfile -> emailValidator.test(userProfile.getEmail())).forEach(profile -> {
            Locale locale = profile.getPreferredLocale();
            if (locale == null || !messageLocales.contains(locale)) {
                locale = defLocale;
            }
            emails.computeIfAbsent(locale, localeKey -> new HashSet<>()).add(profile.getEmail());
        });
        return emails;
    }

    private static Set<UserProfile> getProfilesAllowed(final Sender sender, final Set<Group> groups) {
        return groups.stream().flatMap(Group::getMembers)
                .filter(MessagingSystem.getInstance()::isOptedIn)
                .filter(user -> !sender.getOptInRequired() || sender.getOptedInUsers().contains(user))
                .map(User::getProfile).filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Override
    public void delete() {
        setQueue(null);
        getHandlerSet().forEach(MimeMessageHandler::delete);
        super.delete();
    }
}
