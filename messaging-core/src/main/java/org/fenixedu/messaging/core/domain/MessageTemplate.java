package org.fenixedu.messaging.core.domain;

import org.fenixedu.bennu.MessagingConfiguration;
import org.fenixedu.messaging.core.template.TemplateParameter;
import pt.ist.fenixframework.Atomic;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.fenixedu.bennu.core.i18n.BundleUtil;
import org.fenixedu.commons.i18n.I18N;
import org.fenixedu.commons.i18n.LocalizedString;
import org.fenixedu.messaging.core.exception.MessagingDomainException;
import org.fenixedu.messaging.core.template.DeclareMessageTemplate;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.PebbleEngine.Builder;
import com.mitchellbosecke.pebble.error.PebbleException;
import com.mitchellbosecke.pebble.loader.StringLoader;

import static java.util.Objects.requireNonNull;
import static pt.ist.fenixframework.FenixFramework.atomic;

public class MessageTemplate extends MessageTemplate_Base implements Comparable<MessageTemplate> {
    private static final Map<String, DeclareMessageTemplate> DECLARE_ANNOTATIONS = Maps.newHashMap();
    private static final Map<String, MessageTemplateDeclaration> DECLARATIONS = Maps.newHashMap();
    private static final PebbleEngine PEBBLE_ENGINE;

    static {
        final Builder builder = new PebbleEngine.Builder();
        builder.loader(new StringLoader());
        PEBBLE_ENGINE = builder.autoEscaping(false)
                .newLineTrimming(MessagingConfiguration.getConfiguration().pebbleNewlineTrim())
                .build();
    }

    public static class MessageTemplateDeclaration {

        private final LocalizedString description;
        private final LocalizedString defaultSubject;
        private final LocalizedString defaultTextBody;
        private final LocalizedString defaultHtmlBody;
        private final Map<String, LocalizedString> parameters;

        public LocalizedString getDescription() {
            return description;
        }

        public LocalizedString getDefaultSubject() {
            return defaultSubject;
        }

        public LocalizedString getDefaultTextBody() {
            return defaultTextBody;
        }

        public LocalizedString getDefaultHtmlBody() {
            return defaultHtmlBody;
        }

        public Map<String, LocalizedString> getParameters() {
            return ImmutableMap.copyOf(parameters);
        }

        protected MessageTemplateDeclaration(final DeclareMessageTemplate decl) {
            final String bundle = decl.bundle();
            this.description = localized(decl.description(), bundle);
            this.defaultSubject = localized(decl.subject(), bundle);
            this.defaultTextBody = localized(decl.text(), bundle);
            this.defaultHtmlBody = localized(decl.html(), bundle);
            this.parameters = Arrays.stream(decl.parameters())
                    .collect(Collectors.toMap(TemplateParameter::id, param -> localized(param.description(), bundle)));
        }
    }

    private static LocalizedString localized(final String key, final String bundle) {
        if (key == null) {
            return new LocalizedString();
        }
        if (key.isEmpty() || Strings.isNullOrEmpty(bundle)) {
            return new LocalizedString(I18N.getLocale(), key);
        }
        return BundleUtil.getLocalizedString(bundle, key);
    }

    protected MessageTemplate(final DeclareMessageTemplate declaration) {
        super();
        setMessagingSystem(MessagingSystem.getInstance());
        setId(declaration.id());
        DECLARATIONS.put(getId(), new MessageTemplateDeclaration(declaration));
        reset();
    }

    @Override
    public String getId() {
        // FIXME remove when the framework supports read-only properties
        return super.getId();
    }

    public MessageTemplateDeclaration getDeclaration() {
        return DECLARATIONS.get(getId());
    }

    public boolean isDeclared() {
        return DECLARATIONS.containsKey(getId());
    }

    public Set<Locale> getContentLocales() {
        return Stream.of(getSubject(), getTextBody(), getHtmlBody()).filter(Objects::nonNull)
                .flatMap(content -> content.getLocales().stream()).collect(Collectors.toSet());
    }

    public LocalizedString getCompiledSubject(final Map<String, Object> context) {
        return compile(getId(), getSubject(), context);
    }

    public LocalizedString getCompiledTextBody(final Map<String, Object> context) {
        return compile(getId(), getTextBody(), context);
    }

    public LocalizedString getCompiledHtmlBody(final Map<String, Object> context) {
        return compile(getId(), getHtmlBody(), context);
    }

    private static LocalizedString compile(final String templateId, final LocalizedString template, final Map<String, Object> context) {
        final LocalizedString.Builder builder = new LocalizedString.Builder();
        for (final Locale locale : template.getLocales()) {
            try (StringWriter writer = new StringWriter()) {
                PEBBLE_ENGINE.getTemplate(template.getContent(locale)).evaluate(writer, context, locale);
                builder.with(locale, writer.toString());
            } catch (PebbleException | IOException e) {
                throw MessagingDomainException.malformedTemplate(e, templateId);
            }
        }
        return builder.build();
    }

    public static Set<MessageTemplate> all() {
        return Sets.newHashSet(MessagingSystem.getInstance().getTemplateSet());
    }

    public static Set<MessageTemplate> undeclared() {
        return MessagingSystem.getInstance().getTemplateSet().stream().filter(template -> !template.isDeclared()).collect(Collectors.toSet());
    }

    public static MessageTemplate get(final String templateId) {
        return MessagingSystem.getInstance().getTemplateSet().stream().filter(template -> template.getId().equals(templateId)).findFirst().orElse(null);
    }

    public static void declare(final DeclareMessageTemplate decl) {
        DECLARE_ANNOTATIONS.put(decl.id(), decl);
    }

    public static void reifyDeclarations() {
        all().forEach(template -> {
            DECLARE_ANNOTATIONS.computeIfPresent(template.getId(), (templateId, declaration) -> {
                DECLARATIONS.put(templateId, new MessageTemplateDeclaration(declaration));
                return null;
            });
        });
        DECLARE_ANNOTATIONS.forEach((templateId, declaration) -> atomic(() -> {
            new MessageTemplate(declaration);
        }));
        DECLARE_ANNOTATIONS.clear();
    }

    public void reset() {
        final MessageTemplateDeclaration declaration = getDeclaration();
        if (declaration != null) {
            setSubject(declaration.getDefaultSubject());
            setHtmlBody(declaration.getDefaultHtmlBody());
            setTextBody(declaration.getDefaultTextBody());
        }
    }

    @Override
    public void setSubject(final LocalizedString subject) {
        super.setSubject(requireNonNull(subject));
    }

    @Override
    public void setTextBody(final LocalizedString textBody) {
        super.setTextBody(requireNonNull(textBody));
    }

    @Override
    public void setHtmlBody(final LocalizedString htmlBody) {
        super.setHtmlBody(requireNonNull(htmlBody));
    }

    @Atomic
    public void delete() {
        setMessagingSystem(null);
        deleteDomainObject();
    }

    @Override
    public int compareTo(final MessageTemplate template) {
        final int comparison = getId().compareTo(template.getId());
        return comparison == 0 ? getExternalId().compareTo(template.getExternalId()) : comparison;
    }

}
