import { effect, Injector, runInInjectionContext, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  it('does not make an effect that calls show() re-trigger itself', () => {
    const toast = TestBed.inject(ToastService);
    const trigger = signal<string | null>(null);
    let runs = 0;

    runInInjectionContext(TestBed.inject(Injector), () =>
      effect(() => {
        const message = trigger();
        if (message) {
          runs++;
          toast.show(message);
        }
      })
    );

    trigger.set('Room ended');
    TestBed.tick();
    TestBed.tick();

    expect(runs).toBe(1);
    expect(toast.toasts().map((t) => t.message)).toEqual(['Room ended']);
  });

  it('restarts a repeated message instead of stacking it', () => {
    const toast = TestBed.inject(ToastService);
    toast.show('Copied');
    toast.show('Copied');
    expect(toast.toasts().length).toBe(1);
  });
});
