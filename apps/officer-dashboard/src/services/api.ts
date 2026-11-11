import {
  TicketSummary, TicketDetail, TicketChatMessage, OfficerUser,
  SmsIncomingResponse, SmsHistoryResponse, SmsGatewayStatus,
  IvrCallSession, IvrCallResponse, IvrDtmfPayload, IvrVoicePayload, IvrErrorPayload
} from '../types';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8000/api/v1';

export const api = {
  getBaseUrl: () => API_BASE,
  // sessionStorage limits persistence to the current browser session. The API
  // remains authoritative and every restored session is validated with /me.
  getToken: (): string | null => sessionStorage.getItem('bolkisan_officer_token'),
  setToken: (token: string) => sessionStorage.setItem('bolkisan_officer_token', token),
  clearToken: () => {
    sessionStorage.removeItem('bolkisan_officer_token');
    sessionStorage.removeItem('bolkisan_officer_user');
  },
  getUser: (): OfficerUser | null => {
    const raw = sessionStorage.getItem('bolkisan_officer_user');
    try { return raw ? JSON.parse(raw) : null; } catch {
      sessionStorage.removeItem('bolkisan_officer_token');
      sessionStorage.removeItem('bolkisan_officer_user');
      return null;
    }
  },
  setUser: (user: OfficerUser) => sessionStorage.setItem('bolkisan_officer_user', JSON.stringify(user)),

  async login(officerCode: string, phoneNumber: string, otp: string) {
    const res = await fetch(`${API_BASE}/auth/officer-login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          officer_code: officerCode,
          phone_number: phoneNumber,
          otp: otp
        })
      });
    if (!res.ok) {
      throw new Error((await res.json().catch(() => null))?.detail || 'Officer authentication failed.');
    }
    const data = await res.json();
    this.setToken(data.access_token);
    this.setUser(data.officer);
    return data;
  },

  async validateSession(): Promise<OfficerUser | null> {
    const token = this.getToken();
    if (!token) return null;
    const res = await fetch(`${API_BASE}/auth/me`, { headers: { Authorization: `Bearer ${token}` } });
    if (!res.ok) { this.clearToken(); return null; }
    const data = await res.json();
    if (data.role !== 'extension_officer' || !data.officer_profile) { this.clearToken(); return null; }
    const officer: OfficerUser = {
      id: data.id,
      officer_code: data.officer_profile.officer_code,
      designation: data.officer_profile.designation,
      assigned_district: data.officer_profile.assigned_district,
      assigned_mandals: data.officer_profile.assigned_mandals || [],
      name: data.full_name,
      department: data.officer_profile.department,
    };
    this.setUser(officer);
    return officer;
  },

  async getTickets(statusFilter?: string): Promise<TicketSummary[]> {
    const token = this.getToken();
    const url = statusFilter && statusFilter !== 'all'
      ? `${API_BASE}/tickets/?status_filter=${statusFilter}`
      : `${API_BASE}/tickets/`;

    const res = await fetch(url, {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });
    if (!res.ok) throw new Error(`Unable to load officer tickets (HTTP ${res.status}).`);
    return await res.json();
  },

  async getTicketDetail(id: string): Promise<TicketDetail> {
    const token = this.getToken();
    const res = await fetch(`${API_BASE}/tickets/${id}`, {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });
    if (!res.ok) throw new Error(`Unable to load ticket (HTTP ${res.status}).`);
    return await res.json();
  },

  async resolveTicket(id: string, prescription: string, metadata?: any) {
    const token = this.getToken();
    const res = await fetch(`${API_BASE}/tickets/${id}/resolve`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          prescription,
          action_type: 'chemical_and_cultural',
          ...metadata
        })
      });
    if (!res.ok) throw new Error((await res.json().catch(() => null))?.detail || `Ticket resolution failed (HTTP ${res.status}).`);
    return await res.json();
  },

  async getTicketMessages(ticketId: string): Promise<TicketChatMessage[]> {
    const token = this.getToken();
    const res = await fetch(`${API_BASE}/tickets/${ticketId}/messages`, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });
    if (!res.ok) {
      const err = await res.json().catch(() => null);
      throw new Error(err?.detail || `Failed to fetch ticket messages (HTTP ${res.status})`);
    }
    return await res.json();
  },

  async sendTicketMessage(ticketId: string, content: string, mediaUrl?: string): Promise<TicketChatMessage> {
    const token = this.getToken();
    const res = await fetch(`${API_BASE}/tickets/${ticketId}/messages`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({
        content,
        media_url: mediaUrl,
        msg_type: mediaUrl ? 'image' : 'text'
      })
    });
    if (!res.ok) {
      const err = await res.json().catch(() => null);
      throw new Error(err?.detail || `Failed to send ticket message (HTTP ${res.status})`);
    }
    return await res.json();
  },

  async updateTicketStatus(ticketId: string, status: string): Promise<any> {
    const token = this.getToken();
    const res = await fetch(`${API_BASE}/tickets/${ticketId}/status`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ status })
    });
    if (!res.ok) {
      const err = await res.json().catch(() => null);
      throw new Error(err?.detail || `Failed to update ticket status (HTTP ${res.status})`);
    }
    return await res.json();
  },

  async sendSms(data: { sender_number: string; message_text: string; language?: string; message_id?: string; idempotency_key?: string; simulate_failure?: boolean }): Promise<SmsIncomingResponse> {
    const res = await fetch(`${API_BASE}/sms/incoming`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!res.ok) {
      const err = await res.json().catch(() => null);
      throw new Error(err?.detail || `SMS gateway request failed (HTTP ${res.status})`);
    }
    return await res.json();
  },

  async getSmsHistory(phoneNumber: string): Promise<SmsHistoryResponse> {
    const res = await fetch(`${API_BASE}/sms/history?phone_number=${encodeURIComponent(phoneNumber)}`);
    if (!res.ok) {
      const err = await res.json().catch(() => null);
      throw new Error(err?.detail || `Failed to fetch SMS history (HTTP ${res.status})`);
    }
    return await res.json();
  },

  async clearSmsHistory(phoneNumber: string): Promise<{ success: boolean; cleared_count: number }> {
    const res = await fetch(`${API_BASE}/sms/history?phone_number=${encodeURIComponent(phoneNumber)}`, {
      method: 'DELETE'
    });
    if (!res.ok) {
      const err = await res.json().catch(() => null);
      throw new Error(err?.detail || `Failed to clear SMS history (HTTP ${res.status})`);
    }
    return await res.json();
  },

  async getSmsStatus(): Promise<SmsGatewayStatus> {
    const res = await fetch(`${API_BASE}/sms/status`);
    if (!res.ok) {
      const err = await res.json().catch(() => null);
      throw new Error(err?.detail || `Failed to query SMS gateway status (HTTP ${res.status})`);
    }
    return await res.json();
  },

  // ==========================================
  // IVR Telephony Fallback API Methods
  // ==========================================

  async startIvrCall(data: { caller_number: string; language?: string; farmer_name?: string }): Promise<IvrCallResponse> {
    const res = await fetch(`${API_BASE}/ivr/call/start`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!res.ok) {
      const err = await res.json().catch(() => null);
      throw new Error(err?.detail || `Failed to initiate IVR call (HTTP ${res.status})`);
    }
    return await res.json();
  },

  async sendIvrDtmf(data: IvrDtmfPayload): Promise<IvrCallResponse> {
    const res = await fetch(`${API_BASE}/ivr/call/dtmf`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!res.ok) {
      const err = await res.json().catch(() => null);
      throw new Error(err?.detail || `Failed to process DTMF input (HTTP ${res.status})`);
    }
    return await res.json();
  },

  async sendIvrVoice(data: IvrVoicePayload): Promise<IvrCallResponse> {
    const res = await fetch(`${API_BASE}/ivr/call/voice`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!res.ok) {
      const err = await res.json().catch(() => null);
      throw new Error(err?.detail || `Failed to process voice query (HTTP ${res.status})`);
    }
    return await res.json();
  },

  async simulateIvrError(data: IvrErrorPayload): Promise<IvrCallResponse> {
    const res = await fetch(`${API_BASE}/ivr/call/simulate-error`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!res.ok) {
      const err = await res.json().catch(() => null);
      throw new Error(err?.detail || `Failed to simulate IVR error (HTTP ${res.status})`);
    }
    return await res.json();
  },

  async endIvrCall(callId: string): Promise<IvrCallResponse> {
    const res = await fetch(`${API_BASE}/ivr/call/end`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ call_id: callId })
    });
    if (!res.ok) {
      const err = await res.json().catch(() => null);
      throw new Error(err?.detail || `Failed to terminate IVR call (HTTP ${res.status})`);
    }
    return await res.json();
  },

  async getIvrCallSession(callId: string): Promise<IvrCallSession> {
    const res = await fetch(`${API_BASE}/ivr/call/${encodeURIComponent(callId)}`);
    if (!res.ok) {
      const err = await res.json().catch(() => null);
      throw new Error(err?.detail || `Failed to fetch IVR session (HTTP ${res.status})`);
    }
    return await res.json();
  }
};
