package com.waylo.packagebookingservice.exception;

/** A package order failed at one item. The cause keeps its own HTTP mapping, the handler adds the item index. */
public class PackageItemFailedException extends RuntimeException {

    private final int itemIndex;

    public PackageItemFailedException(int itemIndex, RuntimeException cause) {
        super(cause.getMessage(), cause);
        this.itemIndex = itemIndex;
    }

    public int getItemIndex() { return itemIndex; }
}
