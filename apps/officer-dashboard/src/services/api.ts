import { TicketSummary, TicketDetail, OfficerUser } from '../types';

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
  }
};
