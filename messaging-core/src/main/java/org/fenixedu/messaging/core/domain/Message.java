/*
 * @(#)Message.java
 *
 * Copyright 2012 Instituto Superior Tecnico
 * Founding Authors: Luis Cruz
 *
 *      https://fenix-ashes.ist.utl.pt/
 *
 *   This file is part of the Messaging Module.
 *
 *   The Messaging Module is free software: you can
 *   redistribute it and/or modify it under the terms of the GNU Lesser General
 *   Public License as published by the Free Software Foundation, either version
 *   3 of the License, or (at your option) any later version.
 *
 *   The Messaging Module is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *   GNU Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU Lesser General Public License
 *   along with the Messaging Module. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.fenixedu.messaging.core.domain;

import org.fenixedu.bennu.io.domain.GenericFile;
import pt.ist.fenixframework.Atomic;
import pt.ist.fenixframework.Atomic.TxMode;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.fenixedu.bennu.core.domain.User;
import org.fenixedu.bennu.core.domain.groups.PersistentGroup;
import org.fenixedu.bennu.core.groups.Group;
import org.fenixedu.bennu.core.security.Authenticate;
import org.fenixedu.commons.i18n.I18N;
import org.fenixedu.commons.i18n.LocalizedString;
import org.fenixedu.messaging.core.domain.MessagingSystem.Util;
import org.fenixedu.messaging.core.template.DeclareMessageTemplate;
import org.fenixedu.messaging.core.template.TemplateParameter;
import org.joda.time.DateTime;

import com.google.common.base.Strings;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Objects.requireNonNull;
import static org.fenixedu.messaging.core.domain.MessagingSystem.Util.builderSetAdd;
import static org.fenixedu.messaging.core.domain.MessagingSystem.Util.builderSetCopy;
import static org.fenixedu.messaging.core.domain.MessagingSystem.Util.toEmailSet;

/**
 * @author Luis Cruz
 */
public final class Message extends Message_Base implements Comparable<Message> {

    public static final class TemplateMessageBuilder {

        private final MessageBuilder messageBuilder;
        private final MessageTemplate template;
        private final Map<String, Object> params = new HashMap<>();

        protected TemplateMessageBuilder(final String key, final MessageBuilder messageBuilder) {
            this.template = MessageTemplate.get(key);
            if (this.template == null) {
                throw new IllegalArgumentException("Unknown template key.");
            }
            this.messageBuilder = requireNonNull(messageBuilder);
        }

        public TemplateMessageBuilder parameter(final String paramKey, final Object paramValue) {
            this.params.put(requireNonNull(paramKey), paramValue);
            return this;
        }

        public TemplateMessageBuilder parameters(final Map<String, Object> params) {
            params.entrySet().stream().filter(entry -> entry.getKey() != null && entry.getValue() != null)
                    .forEach(entry -> this.params.put(entry.getKey(), entry.getValue()));
            return this;
        }

        public MessageBuilder and() {
            messageBuilder.subject(template.getCompiledSubject(params));
            messageBuilder.textBody(template.getCompiledTextBody(params));
            messageBuilder.htmlBody(template.getCompiledHtmlBody(params));
            return messageBuilder;
        }
    }

    @DeclareMessageTemplate(id = "org.fenixedu.messaging.message.wrapper",
            description = "message.template.message.wrapper.description", subject = "message.template.message.wrapper.subject",
            text = "message.template.message.wrapper.text", html = "message.template.message.wrapper.html", parameters = {
            @TemplateParameter(id = "sender", description = "message.template.message.wrapper.parameter.sender"),
            @TemplateParameter(id = "creator", description = "message.template.message.wrapper.parameter.creator"),
            @TemplateParameter(id = "replyTo", description = "message.template.message.wrapper.parameter.replyTo"),
            @TemplateParameter(id = "preferredLocale", description = "message.template.message.wrapper.parameter.preferredLocale"),
            @TemplateParameter(id = "subject", description = "message.template.message.wrapper.parameter.subject"),
            @TemplateParameter(id = "textBody", description = "message.template.message.wrapper.parameter.textBody"),
            @TemplateParameter(id = "htmlBody", description = "message.template.message.wrapper.parameter.htmlBody"),
            @TemplateParameter(id = "tos", description = "message.template.message.wrapper.parameter.tos"),
            @TemplateParameter(id = "ccs", description = "message.template.message.wrapper.parameter.ccs"),
            @TemplateParameter(id = "bccs", description = "message.template.message.wrapper.parameter.bccs"),
            @TemplateParameter(id = "singleBccs", description = "message.template.message.wrapper.parameter.singleBccs"),
            @TemplateParameter(id = "singleTos", description = "message.template.message.wrapper.parameter.singleTos") },
            bundle = "MessagingResources")
    public static final class MessageBuilder implements Serializable {
        private static final long serialVersionUID = 525424959825814582L;
        private boolean wrapped;
        private Sender sender;
        private LocalizedString subject = new LocalizedString();
        private LocalizedString textBody = new LocalizedString();
        private LocalizedString htmlBody = new LocalizedString();
        private final Set<String> replyTo = new HashSet<>();
        private Locale preferredLocale = I18N.getLocale();
        private final Set<Group> tos = new HashSet<>();
        private final Set<Group> ccs = new HashSet<>();
        private final Set<Group> bccs = new HashSet<>();
        private final Set<String> singleBccs = new HashSet<>();
        private final Set<GenericFile> files = new HashSet<>();
        private final Set<String> singleTos = new HashSet<>();

        protected MessageBuilder(final Sender sender) {
            from(sender);
        }

        public MessageBuilder from(final Sender sender) {
            this.sender = requireNonNull(sender);
            return this;
        }

        public MessageBuilder wrapped() {
            wrapped = true;
            return this;
        }

        public MessageBuilder unwrapped() {
            wrapped = false;
            return this;
        }

        public MessageBuilder subject(final LocalizedString subject) {
            this.subject = requireNonNull(subject);
            return this;
        }

        public MessageBuilder subject(final String subject, final Locale locale) {
            requireNonNull(locale);
            this.subject = Strings.isNullOrEmpty(subject) ? this.subject.without(locale) : this.subject.with(locale, subject);
            return this;
        }

        public MessageBuilder subject(final String subject) {
            return subject(subject, I18N.getLocale());
        }

        public MessageBuilder textBody(final LocalizedString textBody) {
            this.textBody = requireNonNull(textBody);
            return this;
        }

        public MessageBuilder textBody(final String textBody, final Locale locale) {
            requireNonNull(locale);
            this.textBody =
                    Strings.isNullOrEmpty(textBody) ? this.textBody.without(locale) : this.textBody.with(locale, textBody);
            return this;
        }

        public MessageBuilder textBody(final String textBody) {
            return textBody(textBody, I18N.getLocale());
        }

        public MessageBuilder htmlBody(final LocalizedString htmlBody) {
            this.htmlBody = requireNonNull(htmlBody);
            return this;
        }

        public MessageBuilder htmlBody(final String htmlBody, final Locale locale) {
            requireNonNull(locale);
            this.htmlBody =
                    Strings.isNullOrEmpty(htmlBody) ? this.htmlBody.without(locale) : this.htmlBody.with(locale, htmlBody);
            return this;
        }

        public MessageBuilder htmlBody(final String htmlBody) {
            return htmlBody(htmlBody, I18N.getLocale());
        }

        public TemplateMessageBuilder template(final String key) {
            return new TemplateMessageBuilder(requireNonNull(key), this);
        }

        public MessageBuilder template(final String key, final Map<String, Object> parameters) {
            return new TemplateMessageBuilder(requireNonNull(key), this).parameters(requireNonNull(parameters)).and();
        }

        public MessageBuilder preferredLocale(final Locale preferredLocale) {
            this.preferredLocale = requireNonNull(preferredLocale);
            return this;
        }

        public MessageBuilder content(final String subject, final String textBody, final String htmlBody, final Locale locale) {
            subject(subject, locale);
            textBody(textBody, locale);
            htmlBody(htmlBody, locale);
            return this;
        }

        public MessageBuilder content(final String subject, final String textBody, final String htmlBody) {
            return content(subject, textBody, htmlBody, I18N.getLocale());
        }

        public MessageBuilder content(final LocalizedString subject, final LocalizedString textBody, final LocalizedString htmlBody) {
            subject(subject);
            textBody(textBody);
            htmlBody(htmlBody);
            return this;
        }

        public MessageBuilder to(final Collection<Group> tos) {
            builderSetCopy(requireNonNull(tos), Objects::nonNull, this.tos);
            return this;
        }

        public MessageBuilder to(final Stream<Group> tos) {
            builderSetAdd(requireNonNull(tos), Objects::nonNull, this.tos);
            return this;
        }

        public MessageBuilder to(final Group... tos) {
            builderSetAdd(requireNonNull(tos), Objects::nonNull, this.tos);
            return this;
        }

        public MessageBuilder cc(final Collection<Group> ccs) {
            builderSetCopy(requireNonNull(ccs), Objects::nonNull, this.ccs);
            return this;
        }

        public MessageBuilder cc(final Stream<Group> ccs) {
            builderSetAdd(requireNonNull(ccs), Objects::nonNull, this.ccs);
            return this;
        }

        public MessageBuilder cc(final Group... ccs) {
            builderSetAdd(requireNonNull(ccs), Objects::nonNull, this.ccs);
            return this;
        }

        public MessageBuilder bcc(final Collection<Group> bccs) {
            builderSetCopy(requireNonNull(bccs), Objects::nonNull, this.bccs);
            return this;
        }

        public MessageBuilder bcc(final Stream<Group> bccs) {
            builderSetAdd(requireNonNull(bccs), Objects::nonNull, this.bccs);
            return this;
        }

        public MessageBuilder bcc(final Group... bccs) {
            builderSetAdd(requireNonNull(bccs), Objects::nonNull, this.bccs);
            return this;
        }

        public MessageBuilder singleBcc(final Collection<String> bccs) {
            builderSetCopy(requireNonNull(bccs), Util::isValidEmail, this.singleBccs);
            return this;
        }

        public MessageBuilder singleBcc(final Stream<String> bccs) {
            builderSetAdd(requireNonNull(bccs), Util::isValidEmail, this.singleBccs);
            return this;
        }

        public MessageBuilder singleBcc(final String... bccs) {
            builderSetAdd(requireNonNull(bccs), Util::isValidEmail, this.singleBccs);
            return this;
        }

        public MessageBuilder singleTos(final Collection<String> tos) {
            builderSetCopy(requireNonNull(tos), Util::isValidEmail, this.singleTos);
            return this;
        }

        public MessageBuilder singleTos(final Stream<String> tos) {
            builderSetAdd(requireNonNull(tos), Util::isValidEmail, this.singleTos);
            return this;
        }

        public MessageBuilder singleTos(final String... tos) {
            builderSetAdd(requireNonNull(tos), Util::isValidEmail, this.singleTos);
            return this;
        }

        public MessageBuilder replyTo(final Collection<String> replyTos) {
            builderSetCopy(requireNonNull(replyTos), Util::isValidEmail, this.replyTo);
            return this;
        }

        public MessageBuilder replyTo(final Stream<String> replyTos) {
            builderSetAdd(requireNonNull(replyTos), Util::isValidEmail, this.replyTo);
            return this;
        }

        public MessageBuilder replyTo(final String... replyTos) {
            builderSetAdd(requireNonNull(replyTos), Util::isValidEmail, this.replyTo);
            return this;
        }

        public MessageBuilder attachment(final Collection<GenericFile> files) {
            builderSetCopy(requireNonNull(files), Objects::nonNull, this.files);
            return this;
        }

        public MessageBuilder attachment(final Stream<GenericFile> files) {
            builderSetAdd(requireNonNull(files), Objects::nonNull, this.files);
            return this;
        }

        public MessageBuilder attachment(final GenericFile... files) {
            builderSetAdd(requireNonNull(files), Objects::nonNull, this.files);
            return this;
        }


        public MessageBuilder replyToSender() {
            return replyTo(sender.getReplyTo());
        }

        @Atomic(mode = TxMode.WRITE)
        public Message send() {
            final Message message = new Message();
            message.setSender(sender);
            message.setReplyTo(Strings.emptyToNull(Util.toEmailListString(replyTo)));
            message.setPreferredLocale(preferredLocale);
            tos.stream().map(Group::toPersistentGroup).forEach(message::addTo);
            ccs.stream().map(Group::toPersistentGroup).forEach(message::addCc);
            bccs.stream().map(Group::toPersistentGroup).forEach(message::addBcc);
            message.setSingleBccs(Strings.emptyToNull(Util.toEmailListString(singleBccs)));
            message.setSingleTos(Strings.emptyToNull(Util.toEmailListString(singleTos)));
            if (wrapped) {
                template("org.fenixedu.messaging.message.wrapper").parameter("sender", sender)
                        .parameter("creator", Authenticate.getUser()).parameter("replyTo", replyTo)
                        .parameter("preferredLocale", preferredLocale).parameter("subject", subject)
                        .parameter("textBody", textBody).parameter("htmlBody", htmlBody).parameter("tos", newArrayList(tos))
                        .parameter("ccs", newArrayList(ccs)).parameter("bccs", newArrayList(bccs))
                        .parameter("singleBccs", newArrayList(singleBccs))
                        .parameter("singleTos", newArrayList(singleTos)).and();
            }
            files.forEach(message::addFile);
            message.setSubject(subject);
            message.setTextBody(textBody);
            message.setHtmlBody(htmlBody);
            return message;
        }
    }

    public static MessageBuilder from(final Sender sender) {
        return new MessageBuilder(sender);
    }

    public static MessageBuilder fromSystem() {
        return new MessageBuilder(MessagingSystem.systemSender());
    }

    protected Message() {
        super();
        final MessagingSystem messagingSystem = MessagingSystem.getInstance();
        setMessagingSystem(messagingSystem);
        setMessagingSystemFromPendingDispatch(messagingSystem);
        setCreated(new DateTime());
        setCreator(Authenticate.getUser());
    }

    @Override
    public User getCreator() {
        // FIXME remove when the framework supports read-only properties
        return super.getCreator();
    }

    @Override
    public MessageDispatchReport getDispatchReport() {
        // FIXME remove when the framework supports read-only properties
        return super.getDispatchReport();
    }

    @Override
    public DateTime getCreated() {
        // FIXME remove when the framework supports read-only properties
        return super.getCreated();
    }

    @Override
    public String getReplyTo() {
        // FIXME remove when the framework supports read-only properties
        return super.getReplyTo();
    }

    @Override
    public Sender getSender() {
        // FIXME remove when the framework supports read-only properties
        return super.getSender();
    }

    @Override
    public LocalizedString getSubject() {
        // FIXME remove when the framework supports read-only properties
        return super.getSubject();
    }

    @Override
    public LocalizedString getTextBody() {
        // FIXME remove when the framework supports read-only properties
        return super.getTextBody();
    }

    @Override
    public LocalizedString getHtmlBody() {
        // FIXME remove when the framework supports read-only properties
        return super.getHtmlBody();
    }

    @Override
    public Locale getPreferredLocale() {
        // FIXME remove when the framework supports read-only properties
        return super.getPreferredLocale();
    }

    @Override
    public Set<GenericFile> getFileSet() {
        return super.getFileSet();
    }

    public Set<Group> getToGroups() {
        return getToSet().stream().map(PersistentGroup::toGroup).collect(Collectors.toSet());
    }

    public Set<String> getTos() {
        final Set<String> tos = toEmailSet(getToSet());
        tos.addAll(getSingleTosSet());
        return tos;
    }

    public Set<Group> getCcGroups() {
        return getCcSet().stream().map(PersistentGroup::toGroup).collect(Collectors.toSet());
    }

    public Set<String> getCcs() {
        return toEmailSet(getCcSet());
    }

    public Set<Group> getBccGroups() {
        return getBccSet().stream().map(PersistentGroup::toGroup).collect(Collectors.toSet());
    }

    public Set<String> getBccs() {
        final Set<String> bccs = toEmailSet(getBccSet());
        bccs.addAll(getSingleBccsSet());
        return bccs;
    }

    public Set<String> getSingleBccsSet() {
        return toEmailSet(getSingleBccs());
    }

    public Set<String> getReplyTosSet() {
        return toEmailSet(getReplyTo());
    }

    public Set<String> getSingleTosSet() {
        return toEmailSet(getSingleTos());
    }

    public Set<Locale> getContentLocales() {
        return Stream.of(getSubject(), getTextBody(), getHtmlBody()).filter(Objects::nonNull)
                .flatMap(content -> content.getLocales().stream()).collect(Collectors.toSet());
    }

    public DateTime getSent() {
        return getDispatchReport() == null ? null : getDispatchReport().getFinishedDelivery();
    }

    @Atomic(mode = TxMode.WRITE)
    protected void delete() {
        getToSet().clear();
        getCcSet().clear();
        getBccSet().clear();
        if (getDispatchReport() != null) {
            getDispatchReport().delete();
        }
        setSender(null);
        setCreator(null);
        setMessagingSystemFromPendingDispatch(null);
        setMessagingSystem(null);
        deleteDomainObject();
    }

    public void safeDelete() {
        if (isDeletable()) {
            delete();
        } else {
            throw new IllegalStateException("Message is not deletable by current user at this time.");
        }
    }

    public boolean isDeletable() {
        return getCreator() == null || getCreator().equals(Authenticate.getUser()) && getDispatchReport() == null;
    }

    @Override
    public int compareTo(final Message message) {
        final int comparison = -getCreated().compareTo(message.getCreated());
        return comparison == 0 ? getExternalId().compareTo(message.getExternalId()) : comparison;
    }
}
