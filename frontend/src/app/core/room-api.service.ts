import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

export interface CreateRoomResponse {
  code: string;
  expiresAt: string;
  /** Only the creator gets this; it lets them end the room for everyone. */
  hostToken: string;
}

@Injectable({ providedIn: 'root' })
export class RoomApiService {
  private readonly http = inject(HttpClient);

  createRoom(): Promise<CreateRoomResponse> {
    return firstValueFrom(this.http.post<CreateRoomResponse>(`${environment.apiBaseUrl}/api/rooms`, {}));
  }
}
