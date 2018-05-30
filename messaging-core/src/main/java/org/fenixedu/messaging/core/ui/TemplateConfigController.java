package org.fenixedu.messaging.core.ui;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import org.fenixedu.bennu.core.util.CoreConfiguration;
import org.fenixedu.bennu.spring.portal.SpringFunctionality;
import org.fenixedu.messaging.core.domain.MessageTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import com.google.common.collect.ImmutableMap;

@SpringFunctionality(app = MessagingController.class, title = "title.messaging.templates", accessGroup = "#managers")
@RequestMapping("/messaging/config/templates")
public class TemplateConfigController {

    @RequestMapping(value = { "", "/" })
    public String listTemplates(final Model model, @RequestParam(value = "page", defaultValue = "1") final int page,
            @RequestParam(value = "items", defaultValue = "10") final int items) {
        PaginationUtils.paginate(model, "messaging/config/templates", "templates", MessageTemplate.all(), items, page);
        return "messaging/listTemplates";
    }

    @RequestMapping("/{template}")
    public ModelAndView viewTemplate(@PathVariable final MessageTemplate template) {
        final Set<Locale> locales = new HashSet<>(CoreConfiguration.supportedLocales());
        Stream.of(template.getSubject(), template.getTextBody(), template.getHtmlBody()).flatMap(content -> content.getLocales().stream())
                .forEach(locales::add);
        return new ModelAndView("messaging/viewTemplate",
                ImmutableMap.of("template", template, "locales", getSupportedLocales(template)));
    }

    @RequestMapping("/{template}/edit")
    public String editTemplate(final Model model, @PathVariable final MessageTemplate template,
            @ModelAttribute("templateBean") final MessageContentBean bean) {
        bean.copy(template);
        model.addAttribute("template", template);
        model.addAttribute("templateBean", bean);
        return "messaging/editTemplate";
    }

    @RequestMapping("/{template}/reset")
    public String resetTemplate(final Model model, @PathVariable final MessageTemplate template) {
        model.addAttribute("template", template);
        model.addAttribute("templateBean", new MessageContentBean(template.getDeclaration()));
        return "messaging/editTemplate";
    }

    @RequestMapping(value = "/{template}/edit", method = RequestMethod.POST)
    public ModelAndView saveTemplate(final Model model, @PathVariable final MessageTemplate template,
            @ModelAttribute("templateBean") final MessageContentBean bean) {
        if (bean.edit(template)) {
            return viewTemplate(template);
        }
        model.addAttribute("template", template);
        model.addAttribute("templateBean", bean);
        return new ModelAndView("messaging/editTemplate", model.asMap());
    }

    private Set<Locale> getSupportedLocales(final MessageTemplate template) {
        final Set<Locale> locales = template.getContentLocales();
        locales.addAll(CoreConfiguration.supportedLocales());
        return locales;
    }
}
