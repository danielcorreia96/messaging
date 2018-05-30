package org.fenixedu.messaging.core.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ui.Model;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

public final class PaginationUtils {

    private PaginationUtils() {
    }

    public static <T extends Comparable<T>> List<T> paginate(final Model model, final String path, final String property,
            final Collection<T> items, final int number, final int page) {
        final List<T> list = items.stream().sorted().collect(Collectors.toList());
        return paginateAux(model, path, property, list, number, page);
    }

    public static <T> List<T> paginate(final Model model, final String path, final String property, final Collection<T> items,
            final Comparator<T> comparator, final int number, final int page) {
        final List<T> list = comparator == null ? new ArrayList<>(items) : items.stream().sorted(comparator).collect(Collectors.toList());
        return paginateAux(model, path, property, list, number, page);
    }

    private static <T> List<T> paginateAux(final Model model, final String path, final String property, final List<T> items, int number, int page) {
        if (items.isEmpty()) {
            model.addAttribute("path", path);
            return null;
        }
        number = itemsClip(number, items.size());
        final List<List<T>> pages = Lists.partition(items, number);
        page = pageClip(page, pages.size());
        final List<T> selected = pages.get(page - 1);
        if (model != null) {
            if (!Strings.isNullOrEmpty(property)) {
                model.addAttribute(property, selected);
            }
            model.addAttribute("path", path);
            model.addAttribute("page", page);
            model.addAttribute("items", number);
            model.addAttribute("pages", pages.size());
        }
        return selected;

    }

    private static int itemsClip(final int val, final int max) {
        return val < 1 ? max : val;
    }

    private static int pageClip(int val, final int max) {
        val = val % max;
        return val < 1 ? max + val : val;
    }
}
