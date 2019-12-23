/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.modificator.fadelauncher.model;

import android.os.UserHandle;
import android.util.Log;

import cn.modificator.fadelauncher.AllAppsList;
import cn.modificator.fadelauncher.LauncherAppState;
import cn.modificator.fadelauncher.LauncherModel;
import cn.modificator.fadelauncher.LauncherModel.CallbackTask;
import cn.modificator.fadelauncher.LauncherModel.Callbacks;
import cn.modificator.fadelauncher.LauncherModel.ModelUpdateTask;
import cn.modificator.fadelauncher.ShortcutInfo;
import cn.modificator.fadelauncher.util.ComponentKey;
import cn.modificator.fadelauncher.util.ItemInfoMatcher;
import cn.modificator.fadelauncher.util.MultiHashMap;
import cn.modificator.fadelauncher.widget.WidgetListRowEntry;

import java.util.ArrayList;
import java.util.concurrent.Executor;

/**
 * Extension of {@link ModelUpdateTask} with some utility methods
 */
public abstract class BaseModelUpdateTask implements ModelUpdateTask {

    private static final boolean DEBUG_TASKS = false;
    private static final String TAG = "BaseModelUpdateTask";

    private LauncherAppState mApp;
    private LauncherModel mModel;
    private BgDataModel mDataModel;
    private AllAppsList mAllAppsList;
    private Executor mUiExecutor;

    public void init(LauncherAppState app, LauncherModel model,
            BgDataModel dataModel, AllAppsList allAppsList, Executor uiExecutor) {
        mApp = app;
        mModel = model;
        mDataModel = dataModel;
        mAllAppsList = allAppsList;
        mUiExecutor = uiExecutor;
    }

    @Override
    public final void run() {
        if (!mModel.isModelLoaded()) {
            if (DEBUG_TASKS) {
                Log.d(TAG, "Ignoring model task since loader is pending=" + this);
            }
            // Loader has not yet run.
            return;
        }
        execute(mApp, mDataModel, mAllAppsList);
    }

    /**
     * Execute the actual task. Called on the worker thread.
     */
    public abstract void execute(
            LauncherAppState app, BgDataModel dataModel, AllAppsList apps);

    /**
     * Schedules a {@param task} to be executed on the current callbacks.
     */
    public final void scheduleCallbackTask(final CallbackTask task) {
        final Callbacks callbacks = mModel.getCallback();
        mUiExecutor.execute(() -> {
            Callbacks cb = mModel.getCallback();
            if (callbacks == cb && cb != null) {
                task.execute(callbacks);
            }
        });
    }

    public ModelWriter getModelWriter() {
        // Updates from model task, do not deal with icon position in hotseat. Also no need to
        // verify changes as the ModelTasks always push the changes to callbacks
        return mModel.getWriter(false /* hasVerticalHotseat */, false /* verifyChanges */);
    }


    public void bindUpdatedShortcuts(
            final ArrayList<ShortcutInfo> updatedShortcuts, final UserHandle user) {
        if (!updatedShortcuts.isEmpty()) {
            scheduleCallbackTask(new CallbackTask() {
                @Override
                public void execute(Callbacks callbacks) {
                    callbacks.bindShortcutsChanged(updatedShortcuts, user);
                }
            });
        }
    }

    public void bindDeepShortcuts(BgDataModel dataModel) {
        final MultiHashMap<ComponentKey, String> shortcutMapCopy = dataModel.deepShortcutMap.clone();
        scheduleCallbackTask(new CallbackTask() {
            @Override
            public void execute(Callbacks callbacks) {
                callbacks.bindDeepShortcutMap(shortcutMapCopy);
            }
        });
    }

    public void bindUpdatedWidgets(BgDataModel dataModel) {
        final ArrayList<WidgetListRowEntry> widgets =
                dataModel.widgetsModel.getWidgetsList(mApp.getContext());
        scheduleCallbackTask(new CallbackTask() {
            @Override
            public void execute(Callbacks callbacks) {
                callbacks.bindAllWidgets(widgets);
            }
        });
    }

    public void deleteAndBindComponentsRemoved(final ItemInfoMatcher matcher) {
        getModelWriter().deleteItemsFromDatabase(matcher);

        // Call the components-removed callback
        scheduleCallbackTask(new CallbackTask() {
            @Override
            public void execute(Callbacks callbacks) {
                callbacks.bindWorkspaceComponentsRemoved(matcher);
            }
        });
    }
}
