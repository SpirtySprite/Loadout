package com.kirugoldzzzz.loadout.common.gui;

import com.foliagui.gui.PaginatedGui;
import com.foliagui.item.GuiItem;

import java.util.List;
import java.util.function.Function;

public final class DeferredPage<T> {

    DeferredPage(PaginatedGui gui, List<T> entries, int pageSize, Function<T, GuiItem> renderer) {
        gui.setPageSize(Math.max(1, pageSize));
        gui.setPageItemSupplier(entries.size(), index -> renderer.apply(entries.get(index)));
    }
}
