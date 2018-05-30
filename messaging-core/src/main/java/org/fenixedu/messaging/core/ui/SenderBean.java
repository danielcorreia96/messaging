package org.fenixedu.messaging.core.ui;

import pt.ist.fenixframework.Atomic;
import pt.ist.fenixframework.Atomic.TxMode;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.fenixedu.bennu.core.domain.exceptions.BennuCoreDomainException;
import org.fenixedu.bennu.core.groups.Group;
import org.fenixedu.bennu.core.i18n.BundleUtil;
import org.fenixedu.messaging.core.domain.MessageStoragePolicy;
import org.fenixedu.messaging.core.domain.MessagingSystem;
import org.fenixedu.messaging.core.domain.Sender;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class SenderBean {
    protected static final String BUNDLE = "MessagingResources";

    private Boolean htmlEnabled, allPolicy, nonePolicy, attachmentsEnabled, optInRequired;
    private String name, address, members, replyTo, policy, periodPolicy = "";
    private int amountPolicy = -1;
    private Collection<String> recipients, errors;

    public Collection<String> validate() {
        final Collection<String> errors = Lists.newArrayList();
        final String address = getAddress();
        if (Strings.isNullOrEmpty(address)) {
            errors.add(BundleUtil.getString(BUNDLE, "error.sender.validation.address.empty"));
        }
        if (!MessagingSystem.Util.isValidEmail(address)) {
            errors.add(BundleUtil.getString(BUNDLE, "error.sender.validation.address.invalid", address));
        }
        if (getHtmlEnabled() == null) {
            errors.add(BundleUtil.getString(BUNDLE, "error.sender.validation.html.required"));
        }
        if (getAttachmentsEnabled() == null) {
            errors.add(BundleUtil.getString(BUNDLE, "error.sender.validation.attachmentsEnabled.required"));
        }
        if (getOptInRequired() == null) {
            errors.add(BundleUtil.getString(BUNDLE, "error.sender.validation.optInRequired.required"));
        }
        if (Strings.isNullOrEmpty(getName())) {
            errors.add(BundleUtil.getString(BUNDLE, "error.sender.validation.name.empty"));
        }
        if (Strings.isNullOrEmpty(getPolicy())) {
            errors.add(BundleUtil.getString(BUNDLE, "error.sender.validation.policy.empty"));
        } else {
            try {
                MessageStoragePolicy.internalize(getPolicy());
            } catch (IllegalArgumentException e) {
                errors.add(BundleUtil.getString(BUNDLE, "error.sender.validation.policy.invalid"));
            }
        }
        if (Strings.isNullOrEmpty(getMembers())) {
            errors.add(BundleUtil.getString(BUNDLE, "error.sender.validation.members.empty"));
        } else {
            try {
                Group.parse(getMembers());
            } catch (BennuCoreDomainException e) {
                errors.add(BundleUtil.getString(BUNDLE, "error.sender.validation.members.invalid"));
            }
        }
        final Set<String> recipients = getRecipients();
        if (recipients != null) {
            for (final String recipient : recipients) {
                try {
                    Group.parse(recipient);
                } catch (BennuCoreDomainException e) {
                    errors.add(BundleUtil.getString(BUNDLE, "error.sender.validation.recipient.invalid", recipient));
                }
            }
        }
        final String replyTo = getReplyTo();
        if (!(Strings.isNullOrEmpty(replyTo) || MessagingSystem.Util.isValidEmail(replyTo))) {
            errors.add(BundleUtil.getString(BUNDLE, "error.sender.validation.replyTo.invalid", replyTo));
        }
        setErrors(errors);
        return errors;
    }

    public Boolean getAllPolicy() {
        return allPolicy;
    }

    public Boolean getNonePolicy() {
        return nonePolicy;
    }

    public String getPeriodPolicy() {
        return periodPolicy;
    }

    public int getAmountPolicy() {
        return amountPolicy;
    }

    public Boolean getHtmlEnabled() {
        return htmlEnabled;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getMembers() {
        return members;
    }

    public String getPolicy() {
        return policy;
    }

    public void setPolicy(final String... parts) {
        setPolicy(MessageStoragePolicy.internalize(parts));
    }

    public void setPolicy(final String policy) {
        setPolicy(MessageStoragePolicy.internalize(policy));
    }

    public void setPolicy(final MessageStoragePolicy policy) {
        this.policy = policy.serialize();
        allPolicy = policy.isKeepAll();
        nonePolicy = policy.isKeepNone();
        amountPolicy = policy.getAmount() == null ? -1 : policy.getAmount();
        periodPolicy = policy.getPeriod() == null ? "" : policy.getPeriod().toString().substring(1);
    }

    public Set<String> getRecipients() {
        return recipients == null ? Sets.newHashSet() : Sets.newHashSet(recipients);
    }

    public String getReplyTo() {
        return replyTo;
    }

    public Collection<String> getErrors() {
        return errors;
    }

    public Boolean getAttachmentsEnabled() { return attachmentsEnabled; }

    public Boolean getOptInRequired() { return optInRequired; }

    public void setHtmlEnabled(final boolean htmlEnabled) {
        this.htmlEnabled = htmlEnabled;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public void setAddress(final String address) {
        this.address = address;
    }

    public void setMembers(final String members) {
        this.members = members;
    }

    public void setRecipients(final Collection<String> recipients) {
        this.recipients = recipients;
    }

    public void setReplyTo(final String replyTo) {
        this.replyTo = replyTo;
    }

    protected void setErrors(final Collection<String> errors) {
        this.errors = errors;
    }

    public void setAttachmentsEnabled(final Boolean attachmentsEnabled) { this.attachmentsEnabled = attachmentsEnabled; }

    public void setOptInRequired(final Boolean optInRequired) { this.optInRequired = optInRequired; }

    public Sender newSender() {
        Sender sender = null;
        if (validate().isEmpty()) {
            final Stream<Group> recipients = getRecipients().stream().map(Group::parse);
            sender = Sender.from(getAddress()).as(getName()).members(Group.parse(getMembers()))
                    .storagePolicy(MessageStoragePolicy.internalize(getPolicy())).htmlEnabled(getHtmlEnabled())
                    .replyTo(getReplyTo()).recipients(recipients)
                    .attachmentsEnabled(getAttachmentsEnabled())
                    .optInRequired(getOptInRequired()).build();
        }
        return sender;
    }

    protected void copy(final Sender sender) {
        if (sender != null) {
            setName(sender.getName());
            setAddress(sender.getAddress());
            setPolicy(sender.getPolicy());
            setMembers(sender.getMembers().getExpression());
            setHtmlEnabled(sender.getHtmlEnabled());
            setReplyTo(sender.getReplyTo());
            setRecipients(sender.getRecipients().stream().map(Group::getExpression).collect(Collectors.toSet()));
            setAttachmentsEnabled(sender.getAttachmentsEnabled());
            setOptInRequired(sender.getOptInRequired());
        }
    }

    @Atomic(mode = TxMode.WRITE)
    protected Collection<String> configure(final Sender sender) {
        final Collection<String> errors = validate();
        if (errors.isEmpty()) {
            sender.setName(getName());
            sender.setAddress(getAddress());
            sender.setPolicy(MessageStoragePolicy.internalize(getPolicy()));
            sender.setMembers(Group.parse(getMembers()));
            sender.setHtmlEnabled(getHtmlEnabled());
            sender.setReplyTo(getReplyTo());
            sender.setRecipients(getRecipients().stream().map(Group::parse).collect(Collectors.toSet()));
            sender.setAttachmentsEnabled(attachmentsEnabled);
            sender.setOptInRequired(optInRequired);
        }
        return errors;
    }

}
