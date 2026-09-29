import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { marked } from 'marked';
import { catchError, of, switchMap } from 'rxjs';

// Renders legal/*.md (copied to /legal/ at build, see angular.json assets), so
// the Markdown in the repo is the single source of truth for both documents.
// The drafts' "Not legal advice" note is addressed to the operator, not to
// visitors, so it's dropped before rendering.
const OPERATOR_NOTE = /^> \*\*Not legal advice\.\*\*[\s\S]*?(?=\n\n|\n*$)/m;

@Component({
  selector: 'app-legal-page',
  imports: [RouterLink],
  templateUrl: './legal-page.html',
  styleUrl: './legal-page.scss'
})
export class LegalPage {
  private readonly http = inject(HttpClient);

  /** Route data: which document to show. */
  readonly doc = input.required<'privacy' | 'terms'>();

  private readonly markdown = toSignal(
    toObservable(this.doc).pipe(
      switchMap((doc) =>
        this.http.get(`legal/${doc === 'privacy' ? 'PRIVACY-POLICY' : 'TERMS-OF-SERVICE'}.md`, { responseType: 'text' }).pipe(
          catchError(() => of(null))
        )
      )
    ),
    { initialValue: undefined }
  );

  protected readonly html = computed(() => {
    const md = this.markdown();
    return typeof md === 'string' ? (marked.parse(md.replace(OPERATOR_NOTE, ''), { async: false }) as string) : md;
  });
}
