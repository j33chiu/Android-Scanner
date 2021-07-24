package com.chijo.scanner.itemTouchHelper;

public interface ItemTouchHelperAdapter {
    void onItemMove(int fromPosition, int toPosition);

    void onItemSwiped(int position);
}
