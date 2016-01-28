package com.kickstarter.libs.utils;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.facebook.appevents.AppEventsLogger;
import com.kickstarter.KSApplication;
import com.kickstarter.libs.CurrentConfig;
import com.kickstarter.libs.Koala;
import com.kickstarter.libs.Logout;
import com.kickstarter.libs.rx.transformers.Transformers;
import com.kickstarter.services.ApiClientType;
import com.kickstarter.services.apiresponses.ErrorEnvelope;

import javax.inject.Inject;

public final class ApplicationLifecycleUtil implements Application.ActivityLifecycleCallbacks, ComponentCallbacks2 {
  protected @Inject Koala koala;
  protected @Inject ApiClientType client;
  protected @Inject CurrentConfig config;
  protected @Inject Logout logout;

  private final KSApplication application;
  private boolean isInBackground = true;

  public ApplicationLifecycleUtil(final @NonNull KSApplication application) {
    this.application = application;
    application.component().inject(this);
  }

  @Override
  public void onActivityCreated(final @NonNull Activity activity, final @Nullable Bundle bundle) {
  }

  @Override
  public void onActivityStarted(final @NonNull Activity activity) {
  }

  @Override
  public void onActivityResumed(final @NonNull Activity activity) {
    if(isInBackground){
      koala.trackAppOpen();

      // Facebook: logs 'install' and 'app activate' App Events.
      AppEventsLogger.activateApp(activity);

      // Refresh the config file
      this.client.config()
        .compose(Transformers.pipeApiErrorsTo(this::handleConfigApiError))
        .compose(Transformers.neverError())
        .subscribe(this.config::refresh);

      isInBackground = false;
    }
  }

  /**
   * Handles a config API error by logging the user out in the case of a 401. We will interpret
   * 401's on the config request as meaning the user's current access token is no longer valid,
   * as that endpoint should never 401 othewise.
   */
  private void handleConfigApiError(final @NonNull ErrorEnvelope error) {
    if (error.httpCode() == 401) {
      logout.execute();
      ApplicationUtils.startNewDiscoveryActivity(this.application);
    }
  }

  @Override
  public void onActivityPaused(final @NonNull Activity activity) {
    // Facebook: logs 'app deactivate' App Event.
    AppEventsLogger.deactivateApp(activity);
  }

  @Override
  public void onActivityStopped(final @NonNull Activity activity) {
  }

  @Override
  public void onActivitySaveInstanceState(final @NonNull Activity activity, final @Nullable Bundle bundle) {
  }

  @Override
  public void onActivityDestroyed(final @NonNull Activity activity) {
  }

  @Override
  public void onConfigurationChanged(final @NonNull Configuration configuration) {
  }

  @Override
  public void onLowMemory() {
    koala.trackMemoryWarning();
  }

  /**
   * Memory availability callback. TRIM_MEMORY_UI_HIDDEN means the app's UI is no longer visible.
   * This is triggered when the user navigates out of the app and primarily used to free resources used by the UI.
   * http://developer.android.com/training/articles/memory.html
   */
  @Override
  public void onTrimMemory(final int i) {
    if(i == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
      koala.trackAppClose();
      isInBackground = true;
    }
  }
}
