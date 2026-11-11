import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { TicketSummary, TicketDetail } from '../types';
import { TicketCard } from '../components/TicketCard';
import { TicketDetailModal } from '../components/TicketDetailModal';
import { 
  AlertTriangle, 
  CheckCircle, 
  Clock, 
  RefreshCw, 
  Filter 
} from 'lucide-react';

export const OfficerDashboard: React.FC = () => {
  const [tickets, setTickets] = useState<TicketSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterStatus, setFilterStatus] = useState<string>('all');
  const [selectedTicketDetail, setSelectedTicketDetail] = useState<TicketDetail | null>(null);
  const [selectedMandal, setSelectedMandal] = useState<string>('all');

  const loadTickets = async () => {
    setLoading(true);
    try {
      const data = await api.getTickets(filterStatus === 'all' ? undefined : filterStatus);
      setTickets(data);
    } catch (err) {
      console.error('Error fetching tickets:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTickets();
  }, [filterStatus]);

  const handleSelectTicket = async (summary: TicketSummary) => {
    try {
      const detail = await api.getTicketDetail(summary.id);
      setSelectedTicketDetail(detail);
    } catch (err) {
      alert('Error loading ticket detail: ' + (err as Error).message);
    }
  };

  const handleResolve = async (prescription: string, metadata?: any) => {
    if (!selectedTicketDetail) return;
    await api.resolveTicket(selectedTicketDetail.id, prescription, metadata);
    await loadTickets();
  };

  // Metrics
  const isTicketOpen = (t: TicketSummary) => {
    const s = (t.status || '').toLowerCase();
    return s !== 'resolved' && s !== 'closed';
  };
  const isTicketResolved = (t: TicketSummary) => {
    const s = (t.status || '').toLowerCase();
    return s === 'resolved' || s === 'closed';
  };

  const totalCount = tickets.length;
  const urgentCount = tickets.filter(t => (t.priority || '').toLowerCase() === 'high' || (t.priority || '').toLowerCase() === 'emergency').length;
  const openCount = tickets.filter(isTicketOpen).length;
  const resolvedCount = tickets.filter(isTicketResolved).length;

  const filteredTickets = selectedMandal === 'all' 
    ? tickets 
    : tickets.filter(t => t.mandal.toLowerCase() === selectedMandal.toLowerCase());

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Top Banner / Metrics Overview */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase text-slate-500">Total Escalations</span>
            <div className="p-2 bg-slate-100 rounded-xl text-slate-600">
              <Clock className="w-5 h-5" />
            </div>
          </div>
          <p className="text-2xl font-black text-slate-900 mt-2">{totalCount}</p>
          <span className="text-xs text-slate-400">Jurisdiction queue count</span>
        </div>

        <div className="bg-white p-5 rounded-2xl shadow-sm border border-red-200 bg-red-50/20">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase text-red-600">Urgent Safety Risks</span>
            <div className="p-2 bg-red-100 rounded-xl text-red-600">
              <AlertTriangle className="w-5 h-5" />
            </div>
          </div>
          <p className="text-2xl font-black text-red-700 mt-2">{urgentCount}</p>
          <span className="text-xs text-red-600 font-medium">Pesticide & Quarantine Alerts</span>
        </div>

        <div className="bg-white p-5 rounded-2xl shadow-sm border border-amber-200">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase text-amber-600">Awaiting Review</span>
            <div className="p-2 bg-amber-100 rounded-xl text-amber-600">
              <Clock className="w-5 h-5" />
            </div>
          </div>
          <p className="text-2xl font-black text-amber-700 mt-2">{openCount}</p>
          <span className="text-xs text-slate-400">Needs officer prescription</span>
        </div>

        <div className="bg-white p-5 rounded-2xl shadow-sm border border-green-200">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase text-green-600">Resolved Cases</span>
            <div className="p-2 bg-green-100 rounded-xl text-green-600">
              <CheckCircle className="w-5 h-5" />
            </div>
          </div>
          <p className="text-2xl font-black text-green-700 mt-2">{resolvedCount}</p>
          <span className="text-xs text-slate-400">Voice feedback dispatched</span>
        </div>
      </div>

      {/* Filter and Action Bar */}
      <div className="bg-white p-4 rounded-2xl shadow-sm border border-slate-200 flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-wrap items-center space-x-2">
          <button
            onClick={() => setFilterStatus('all')}
            className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${
              filterStatus === 'all' ? 'bg-forest-700 text-white shadow-sm' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
            }`}
          >
            All Tickets ({totalCount})
          </button>
          <button
            onClick={() => setFilterStatus('open')}
            className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${
              filterStatus === 'open' ? 'bg-amber-600 text-white shadow-sm' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
            }`}
          >
            Open ({openCount})
          </button>
          <button
            onClick={() => setFilterStatus('resolved')}
            className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${
              filterStatus === 'resolved' ? 'bg-forest-700 text-white shadow-sm' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
            }`}
          >
            Resolved ({resolvedCount})
          </button>
        </div>

        <div className="flex items-center space-x-3">
          <div className="flex items-center space-x-2 text-xs text-slate-600">
            <Filter className="w-4 h-4 text-slate-400" />
            <span className="font-semibold">Mandal:</span>
            <select
              value={selectedMandal}
              onChange={(e) => setSelectedMandal(e.target.value)}
              className="border border-slate-300 rounded-xl px-2.5 py-1.5 text-xs font-medium focus:ring-1 focus:ring-forest-600 bg-white"
            >
              <option value="all">All Assigned Mandals</option>
              <option value="Geesugonda">Geesugonda</option>
              <option value="Wardhannapet">Wardhannapet</option>
              <option value="Atmakur">Atmakur</option>
            </select>
          </div>

          <button
            onClick={loadTickets}
            className="flex items-center space-x-1.5 text-xs text-slate-700 hover:text-slate-900 border border-slate-200 px-3 py-1.5 rounded-xl hover:bg-slate-50 transition-colors font-semibold"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* Ticket List Stream */}
      <div className="space-y-3">
        {loading ? (
          <div className="bg-white rounded-2xl p-12 text-center text-slate-500 space-y-2 border border-slate-200">
            <RefreshCw className="w-6 h-6 animate-spin mx-auto text-forest-700" />
            <p className="text-sm font-medium">Synchronizing Mandal Agricultural Escalations...</p>
          </div>
        ) : filteredTickets.length === 0 ? (
          <div className="bg-white rounded-2xl p-12 text-center text-slate-500 space-y-2 border border-slate-200">
            <CheckCircle className="w-8 h-8 text-forest-600 mx-auto" />
            <p className="text-base font-bold text-slate-800">No Escalated Tickets in this Filter</p>
            <p className="text-xs text-slate-500">All farmer inquiries in your assigned mandals are currently addressed.</p>
          </div>
        ) : (
          filteredTickets.map((ticket) => (
            <TicketCard
              key={ticket.id}
              ticket={ticket}
              onSelect={handleSelectTicket}
            />
          ))
        )}
      </div>

      {/* Detail & Prescription Modal */}
      {selectedTicketDetail && (
        <TicketDetailModal
          ticket={selectedTicketDetail}
          onClose={() => setSelectedTicketDetail(null)}
          onResolve={handleResolve}
          onStatusChange={async () => {
            await loadTickets();
          }}
        />
      )}
    </div>
  );
};
