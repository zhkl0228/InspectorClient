package org.jf.dexlib2.iface;

import javax.annotation.Nonnull;

/**
 * This class represents a dex file that is contained in a MultiDexContainer
 */
public interface MultiDexFile extends DexFile {
    /**
     * @return The name of this entry within its container
     */
    @Nonnull
    String getEntryName();

    /**
     * @return The MultiDexContainer that contains this dex file
     */
    @Nonnull MultiDexContainer<? extends MultiDexFile> getContainer();
}
