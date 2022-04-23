/***************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.format.Formatter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ichi2.anki.exception.StorageAccessException;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Storage;
import com.ichi2.preferences.PreferenceExtensions;
import com.ichi2.utils.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.VisibleForTesting;
import timber.log.Timber;

/**
 * Singleton which opens, stores, and closes the reference to the Collection.
 */
public class CollectionHelper {

    // Collection instance belonging to sInstance
    private Collection mCollection;
    // Path to collection, cached for the reopenCollection() method
    private String mPath;
    // Name of anki2 file
    public static final String COLLECTION_FILENAME = "collection.anki2";
    public static final String PREF_DECK_PATH = "deckPath";
    public static final String CURRENT_PROFILE_NAME = "profile_name";
    public static final String DEFAULT_PROFILE_NAME = "Default";
    public static final String ANKI_ROOT = "Nestor";

    /**
     * Prevents {@link com.ichi2.async.CollectionLoader} from spuriously re-opening the {@link Collection}.
     *
     * <p>Accessed only from synchronized methods.
     */
    private boolean mCollectionLocked;


    @Nullable
    public static Long getCollectionSize(Context context) {
        try {
            String path = getCollectionPath(context);
            return new File(path).length();
        } catch (Exception e) {
            Timber.e(e, "Error getting collection Length");
            return null;
        }
    }


    public synchronized void lockCollection() {
        mCollectionLocked = true;
    }


    public synchronized void unlockCollection() {
        mCollectionLocked = false;
    }


    public synchronized boolean isCollectionLocked() {
        return mCollectionLocked;
    }


    /**
     * Lazy initialization holder class idiom. High performance and thread safe way to create singleton.
     */
    @VisibleForTesting
    public static class LazyHolder {
        @VisibleForTesting
        public static CollectionHelper INSTANCE = new CollectionHelper();
    }


    /**
     * @return Singleton instance of the helper class
     */
    public static CollectionHelper getInstance() {
        return LazyHolder.INSTANCE;
    }


    /**
     * Get the single instance of the {@link Collection}, creating it if necessary  (lazy initialization).
     *
     * @param context context which can be used to get the setting for the path to the Collection
     * @return instance of the Collection
     */
    public synchronized Collection getCol(Context context) {
        // Open collection
        if (!colIsOpen()) {
            String path = getCollectionPath(context);
            // Check that the directory has been created and initialized
            try {
                initializeAnkiDroidDirectory(getParentDirectory(path));
                mPath = path;
            } catch (StorageAccessException e) {
                Timber.e(e, "Could not initialize AnkiDroid directory");
                return null;
            }
            // Open the database
            Timber.i("Begin openCollection: %s", path);
            mCollection = Storage.Collection(context, path, false, true);
            Timber.i("End openCollection: %s", path);
        }
        return mCollection;
    }


    /**
     * Call getCol(context) inside try / catch statement.
     * Send exception report and return null if there was an exception.
     *
     * @param context
     * @return
     */
    public synchronized Collection getColSafe(Context context) {
        try {
            return getCol(context);
        } catch (Exception e) {
            AnkiDroidApp.sendExceptionReport(e, "CollectionHelper.getColSafe");
            return null;
        }
    }


    /**
     * Close the {@link Collection}, optionally saving
     *
     * @param save whether or not save before closing
     */
    public synchronized void closeCollection(boolean save, String reason) {
        Timber.i("closeCollection: %s", reason);
        if (mCollection != null) {
            mCollection.close(save);
        }
    }


    /**
     * @return Whether or not {@link Collection} and its child database are open.
     */
    public boolean colIsOpen() {
        return mCollection != null && mCollection.getDb() != null &&
                mCollection.getDb().getDatabase() != null && mCollection.getDb().getDatabase().isOpen();
    }


    /**
     * Create the AnkiDroid directory if it doesn't exist and add a .nomedia file to it if needed.
     * <p>
     * The AnkiDroid directory is a user preference stored under the "deckPath" key, and a sensible
     * default is chosen if the preference hasn't been created yet (i.e., on the first run).
     * <p>
     * The presence of a .nomedia file indicates to media scanners that the directory must be
     * excluded from their search. We need to include this to avoid media scanners including
     * media files from the collection.media directory. The .nomedia file works at the directory
     * level, so placing it in the AnkiDroid directory will ensure media scanners will also exclude
     * the collection.media sub-directory.
     *
     * @param path Directory to initialize
     * @throws StorageAccessException If no write access to directory
     */
    public static synchronized void initializeAnkiDroidDirectory(String path) throws StorageAccessException {
        // Create specified directory if it doesn't exit
        File dir = new File(path);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new StorageAccessException("Failed to create AnkiDroid directory " + path);
        }
        if (!dir.canWrite()) {
            throw new StorageAccessException("No write access to AnkiDroid directory " + path);
        }
        // Add a .nomedia file to it if it doesn't exist
        File nomedia = new File(dir, ".nomedia");
        if (!nomedia.exists()) {
            try {
                nomedia.createNewFile();
            } catch (IOException e) {
                throw new StorageAccessException("Failed to create .nomedia file", e);
            }
        }
    }


    public static boolean deleteProfile(String profile) {
        File profileDir = new File(Environment.getExternalStorageDirectory(), ANKI_ROOT + "/" + profile);
        return deleteRecursive(profileDir);
    }


    private static boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }

        return fileOrDirectory.delete();
    }


    /**
     * Try to access the current AnkiDroid directory
     *
     * @param context to get directory with
     * @return whether or not dir is accessible
     */
    public static boolean isCurrentAnkiDroidDirAccessible(Context context) {
        try {
            initializeAnkiDroidDirectory(getCurrentAnkiDroidDirectory(context));
            return true;
        } catch (StorageAccessException e) {
            return false;
        }
    }


    /**
     * Get the absolute path to a directory that is suitable to be the default starting location
     * for the AnkiDroid folder. This is a folder named "AnkiDroid" at the top level of the
     * external storage directory.
     *
     * @return the folder path
     */
    public static String getDefaultAnkiDroidDirectory() {
        return new File(Environment.getExternalStorageDirectory(), ANKI_ROOT + "/Default").getAbsolutePath();
    }


    public static List<String> getAnkiProfiles() {
        File ankiRoot = new File(Environment.getExternalStorageDirectory(), ANKI_ROOT);
        ArrayList<String> profile = new ArrayList<>();
        if (ankiRoot.exists() && ankiRoot.canRead()) {
            File[] files = ankiRoot.listFiles();
            if (files != null) {
                Arrays.sort(files, (o1, o2) -> (int) (o2.lastModified() - o1.lastModified()));
                for (File file : files) {
                    if (file.isDirectory()) {
                        profile.add(file.getName());
                    }
                }
            }
        }
        return profile;
    }


    public static String createCustomProfileDirectory(String profile) throws StorageAccessException {
        if (profile.isEmpty()) {
            throw new StorageAccessException("Invalid profile name");
        } else {
            return new File(Environment.getExternalStorageDirectory(), ANKI_ROOT + "/" + profile).getAbsolutePath();
        }
    }


    /**
     * @return the path to the actual {@link Collection} file
     */
    public static String getCollectionPath(Context context) {
        return new File(getCurrentAnkiDroidDirectory(context), COLLECTION_FILENAME).getAbsolutePath();
    }


    /**
     * @return the absolute path to the AnkiDroid directory.
     */
    public static String getCurrentAnkiDroidDirectory(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        return PreferenceExtensions.getOrSetString(
                preferences,
                "deckPath",
                CollectionHelper::getDefaultAnkiDroidDirectory);
    }


    /**
     * Get parent directory given the {@link Collection} path.
     *
     * @param path path to AnkiDroid collection
     * @return path to AnkiDroid folder
     */
    private static String getParentDirectory(String path) {
        return new File(path).getParentFile().getAbsolutePath();
    }


    /**
     * This currently stores either:
     * An error message stating the reason that a storage check must be performed
     * OR
     * The current storage requirements, and the current available storage.
     */
    public static class CollectionIntegrityStorageCheck {

        @Nullable
        private final String mErrorMessage;

        //OR:
        @Nullable
        private final Long mRequiredSpace;
        @Nullable
        private final Long mFreeSpace;


        private CollectionIntegrityStorageCheck(long requiredSpace, long freeSpace) {
            this.mFreeSpace = freeSpace;
            this.mRequiredSpace = requiredSpace;
            this.mErrorMessage = null;
        }


        private CollectionIntegrityStorageCheck(@NonNull String errorMessage) {
            this.mRequiredSpace = null;
            this.mFreeSpace = null;
            this.mErrorMessage = errorMessage;
        }


        private static CollectionIntegrityStorageCheck fromError(String errorMessage) {
            return new CollectionIntegrityStorageCheck(errorMessage);
        }


        private static String defaultRequiredFreeSpace(Context context) {
            long oneHundredFiftyMB = 150 * 1000 * 1000; //tested, 1024 displays 157MB. 1000 displays 150
            return Formatter.formatShortFileSize(context, oneHundredFiftyMB);
        }


        public static CollectionIntegrityStorageCheck createInstance(Context context) {

            Long maybeCurrentCollectionSizeInBytes = getCollectionSize(context);
            if (maybeCurrentCollectionSizeInBytes == null) {
                Timber.w("Error obtaining collection file size.");
                String requiredFreeSpace = defaultRequiredFreeSpace(context);
                return fromError(context.getResources().getString(R.string.integrity_check_insufficient_space, requiredFreeSpace));
            }

            // This means that when VACUUMing a database, as much as twice the size of the original database file is
            // required in free disk space. - https://www.sqlite.org/lang_vacuum.html
            long requiredSpaceInBytes = maybeCurrentCollectionSizeInBytes * 2;

            // We currently use the same directory as the collection for VACUUM/ANALYZE due to the SQLite APIs
            File collectionFile = new File(getCollectionPath(context));
            long freeSpace = FileUtil.getFreeDiskSpace(collectionFile, -1);

            if (freeSpace == -1) {
                Timber.w("Error obtaining free space for '%s'", collectionFile.getPath());
                String readableFileSize = Formatter.formatFileSize(context, requiredSpaceInBytes);
                return fromError(context.getResources().getString(R.string.integrity_check_insufficient_space, readableFileSize));
            }

            return new CollectionIntegrityStorageCheck(requiredSpaceInBytes, freeSpace);
        }


        public boolean shouldWarnOnIntegrityCheck() {
            return this.mErrorMessage != null || fileSystemDoesNotHaveSpaceForBackup();
        }


        private boolean fileSystemDoesNotHaveSpaceForBackup() {
            //only to be called when mErrorMessage == null
            if (mFreeSpace == null || mRequiredSpace == null) {
                Timber.e("fileSystemDoesNotHaveSpaceForBackup called in invalid state.");
                return true;
            }
            Timber.d("Required Free Space: %d. Current: %d", mRequiredSpace, mFreeSpace);
            return mRequiredSpace > mFreeSpace;
        }


        public String getWarningDetails(Context context) {
            if (mErrorMessage != null) {
                return mErrorMessage;
            }
            if (mFreeSpace == null || mRequiredSpace == null) {
                Timber.e("CollectionIntegrityCheckStatus in an invalid state");
                String defaultRequiredFreeSpace = defaultRequiredFreeSpace(context);
                return context.getResources().getString(R.string.integrity_check_insufficient_space, defaultRequiredFreeSpace);
            }

            String required = Formatter.formatShortFileSize(context, mRequiredSpace);
            String insufficientSpace = context.getResources().getString(
                    R.string.integrity_check_insufficient_space, required);

            //Also concat in the extra content showing the current free space.
            String currentFree = Formatter.formatShortFileSize(context, mFreeSpace);
            String insufficientSpaceCurrentFree = context.getResources().getString(
                    R.string.integrity_check_insufficient_space_extra_content, currentFree);
            return insufficientSpace + insufficientSpaceCurrentFree;
        }
    }


    /**
     * Fetches additional collection data not required for
     * application startup
     * <p>
     * Allows mandatory startup procedures to return early, speeding up startup. Less important tasks are offloaded here
     * No-op if data is already fetched
     */
    public static void loadCollectionComplete(Collection col) {
        col.getModels();
    }

}
