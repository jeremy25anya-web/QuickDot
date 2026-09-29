import { ApplicationConfig, ErrorHandler, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { createErrorHandler } from '@sentry/angular';

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Input binding: route data (e.g. the legal page's `doc`) arrives as inputs.
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(),
    // Reports uncaught Angular errors to Sentry; a no-op until initSentry()
    // has a DSN (see core/sentry.ts).
    { provide: ErrorHandler, useValue: createErrorHandler() }
  ]
};
