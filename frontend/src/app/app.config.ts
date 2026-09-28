import { ApplicationConfig, ErrorHandler, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { createErrorHandler } from '@sentry/angular';

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(),
    // Reports uncaught Angular errors to Sentry; a no-op until initSentry()
    // has a DSN (see core/sentry.ts).
    { provide: ErrorHandler, useValue: createErrorHandler() }
  ]
};
