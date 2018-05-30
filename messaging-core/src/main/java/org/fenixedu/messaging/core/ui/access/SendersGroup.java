package org.fenixedu.messaging.core.ui.access;

import java.util.stream.Stream;

import org.fenixedu.bennu.core.annotation.GroupOperator;
import org.fenixedu.bennu.core.domain.User;
import org.fenixedu.bennu.core.groups.Group;
import org.fenixedu.bennu.core.groups.GroupStrategy;
import org.fenixedu.bennu.core.i18n.BundleUtil;
import org.fenixedu.messaging.core.domain.Sender;
import org.joda.time.DateTime;

@GroupOperator("senders")
public class SendersGroup extends GroupStrategy {
    private static final long serialVersionUID = 3721966861390742190L;

    private static final String BUNDLE = "MessagingResources";
    private static SendersGroup senders = new SendersGroup();
    public static Group get() {
        return senders;
    }

    @Override
    public String getPresentationName() {
        return BundleUtil.getString(BUNDLE, "label.messaging.group.senders");
    }

    @Override
    public Stream<User> getMembers() {
        return Sender.all().stream().map(Sender::getMembers).flatMap(Group::getMembers);
    }

    @Override
    public Stream<User> getMembers(final DateTime when) {
        return Sender.all().stream().map(Sender::getMembers).flatMap(group -> group.getMembers(when));
        //FIXME We do not store temporal information of sender member groups
    }

    @Override
    public boolean isMember(final User user) {
        return !Sender.available(user).isEmpty();
    }

    @Override
    public boolean isMember(final User user, final DateTime when) {
        return isMember(user);  //FIXME We do not store temporal information of sender member groups
    }

}
