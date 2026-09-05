import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

export interface CreateRoomResponse {
  code: string;
  expiresAt: string;
}

@Injectable({ providedIn: 'root' })
export class RoomApiService {
  private readonly http = inject(HttpClient);

  createRoom(): Promise<CreateRoomResponse> {
    return firstValueFrom(this.http.post<CreateRoomResponse>(`${environment.apiBaseUrl}/api/rooms`, {}));
  }
}
