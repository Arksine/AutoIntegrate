package com.arksine.autointegrate.utilities;
import android.graphics.drawable.Drawable;

/**
 * Created by Eric on 10/17/2016.
 */

public class AppItem {

    private static String TAG = "AppItem";

    private String mItemName;
    private String mPackageName;
    private Drawable mItemImage;

    public AppItem(String itemName, String packageName, Drawable itemImage) {
        this.mItemName = itemName;
        this.mPackageName = packageName;
        this.mItemImage = itemImage;
    }

    public String getItemName() {
        return mItemName;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public Drawable getItemImage() {
        return mItemImage;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AppItem) {
            AppItem cmp = (AppItem) obj;
            return (this.mItemName.equals(cmp.mItemName)) &&
                    (this.mPackageName.equals(cmp.mPackageName)) &&
                    (this.mItemImage.equals(cmp.mItemImage));

        } else if (obj instanceof String) {
            // we will allow string comparisons, so comparing the array adapter is easy
            String packageName = (String) obj;
            return (this.mPackageName.equals(packageName));
        } else {
            return false;
        }
    }
}
