package org.fenixedu.messaging.core.bootstrap;

import java.util.List;

import org.fenixedu.bennu.core.bootstrap.AdminUserBootstrapper;
import org.fenixedu.bennu.core.bootstrap.BootstrapError;
import org.fenixedu.bennu.core.bootstrap.annotations.Bootstrap;
import org.fenixedu.bennu.core.bootstrap.annotations.Bootstrapper;
import org.fenixedu.bennu.core.bootstrap.annotations.Field;
import org.fenixedu.bennu.core.bootstrap.annotations.FieldType;
import org.fenixedu.bennu.core.bootstrap.annotations.Section;
import org.fenixedu.bennu.core.domain.exceptions.BennuCoreDomainException;
import org.fenixedu.bennu.core.groups.Group;
import org.fenixedu.messaging.core.bootstrap.MessagingSystemBootstrap.SystemSenderSection;
import org.fenixedu.messaging.core.domain.MessagingSystem;
import org.fenixedu.messaging.core.domain.Sender;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

@Bootstrapper(bundle = "MessagingResources", name = "title.bootstrapper", sections = SystemSenderSection.class,
        after = AdminUserBootstrapper.class)
public final class MessagingSystemBootstrap {

    private static final String BUNDLE = "MessagingResources";
    public static final String DEFAULT_SYSTEM_SENDER_ADDRESS = "system@messaging.fenixedu.org";
    public static final String DEFAULT_SYSTEM_SENDER_NAME = "System Sender";
    public static final String DEFAULT_SYSTEM_SENDER_MEMBERS = "#managers";

    private MessagingSystemBootstrap(){
        // Utility classes should have a private constructor to prevent instantiation.
    }

    @Bootstrap
    public static List<BootstrapError> bootstrapSystemSender(final SystemSenderSection section) {
        final String name = section.getName();
        final String address = section.getAddress();
        final String expression = section.getGroupExpression();
        final List<BootstrapError> errors = Lists.newArrayList();
        if (Strings.isNullOrEmpty(name)) {
            errors.add(new BootstrapError(SystemSenderSection.class, "getName", "error.bootstrapper.systemsender.name.empty",
                    BUNDLE));
        }
        if (Strings.isNullOrEmpty(address)) {
            errors.add(
                    new BootstrapError(SystemSenderSection.class, "getAddress", "error.bootstrapper.systemsender.address.empty",
                            BUNDLE));
        }
        if (!MessagingSystem.Util.isValidEmail(address)) {
            errors.add(
                    new BootstrapError(SystemSenderSection.class, "getAddress", "error.bootstrapper.systemsender.address.invalid",
                            BUNDLE));
        }
        if (Strings.isNullOrEmpty(expression)) {
            errors.add(new BootstrapError(SystemSenderSection.class, "getGroupExpression",
                    "error.bootstrapper.systemsender.group.empty", BUNDLE));
        }
        Group group = null;
        try {
            group = Group.parse(expression);
        } catch (BennuCoreDomainException e) {
            errors.add(new BootstrapError(SystemSenderSection.class, "getGroupExpression",
                    "error.bootstrapper.systemsender.group.invalid", BUNDLE));
        }
        if (errors.isEmpty()) {
            final Sender sender = MessagingSystem.systemSender();
            sender.setName(name);
            sender.setAddress(address);
            sender.setMembers(group);
        }
        return errors;
    }

    @Section(name = "title.bootstrapper.systemsender", description = "title.bootstrapper.systemsender.description",
            bundle = BUNDLE)
    public interface SystemSenderSection {
        @Field(name = "label.bootstrapper.systemsender.name", defaultValue = DEFAULT_SYSTEM_SENDER_NAME, order = 1)
        String getName();

        @Field(name = "label.bootstrapper.systemsender.address", defaultValue = DEFAULT_SYSTEM_SENDER_ADDRESS,
                fieldType = FieldType.EMAIL, order = 2)
        String getAddress();

        @Field(name = "label.bootstrapper.systemsender.group", hint = "hint.bootstrapper.systemsender.group", defaultValue = DEFAULT_SYSTEM_SENDER_MEMBERS,
                order = 3)
        String getGroupExpression();
    }
}
