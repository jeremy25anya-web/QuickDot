import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { initSentry } from './app/core/sentry';

// index.html flags browsers too old for Angular and shows an "update your
// browser" note instead; starting the app there would only crash.
if (!document.documentElement.hasAttribute('data-unsupported')) {
  initSentry();

  bootstrapApplication(App, appConfig)
    .catch((err) => console.error(err));
}
