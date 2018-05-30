package org.fenixedu.messaging.core.dispatch;

import org.fenixedu.messaging.core.domain.Message;
import org.fenixedu.messaging.core.domain.MessageDispatchReport;

public interface MessageDispatcher {
    MessageDispatchReport dispatch(Message message);
}
