import React, { useState, useEffect } from 'react';
import { api } from './services/api';
import { OfficerUser } from './types';
import { Navbar } from './components/Navbar';
import { OfficerDashboard } from './pages/OfficerDashboard';
import { OfficerLogin } from './pages/OfficerLogin';
import { FarmerMobileSimulator } from './components/FarmerMobileSimulator';
import { Smartphone, Building2, SplitSquareVertical } from 'lucide-react';

export const App: React.FC = () => {
  const [user, setUser] = useState<OfficerUser | null>(null);
  const [initialized, setInitialized] = useState(false);
  const [viewMode, setViewMode] = useState<'split' | 'farmer' | 'officer'>('split');
  const [escalationTrigger, setEscalationTrigger] = useState(0);

  useEffect(() => {
    const existingUser = api.getUser();
    const token = api.getToken();
    if (token && existingUser) {
      setUser(existingUser);
    } else {
      // Auto-initialize demo officer so user immediately sees live data
      api.login('AGRI-TS-042', '9123456789', '654321')
        .then(() => {
          setUser(api.getUser());
        })
        .catch(() => {});
    }
    setInitialized(true);
  }, []);

  const handleLogout = () => {
    api.clearToken();
    setUser(null);
  };

  const handleLoginSuccess = () => {
    const u = api.getUser();
    setUser(u);
  };

  const handleTicketEscalated = () => {
    setEscalationTrigger(prev => prev + 1);
  };

  if (!initialized) {
    return null;
  }

  return (
    <div className="min-h-screen bg-slate-900 text-slate-100 flex flex-col font-sans select-none">
      {/* Top Interactive Mode Navigation Bar */}
      <header className="bg-forest-950 border-b border-forest-800 px-4 py-2.5 flex items-center justify-between shadow-lg">
        <div className="flex items-center space-x-3">
          <div className="w-9 h-9 rounded-xl bg-forest-700 flex items-center justify-center text-white border border-forest-500 shadow-sm">
            <span className="font-serif font-black text-lg">బో</span>
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <span className="text-lg font-black tracking-tight text-white font-serif">BOL KISAN</span>
              <span className="bg-emerald-600 text-white text-[10px] font-bold px-2 py-0.5 rounded-full uppercase tracking-wider">
                Live Full-Stack Demo
              </span>
            </div>
            <p className="text-[11px] text-emerald-200">
              Multilingual Indic Agricultural Voice AI & Officer Operations Platform
            </p>
          </div>
        </div>

        {/* View Switcher Tabs */}
        <div className="flex bg-slate-800/90 p-1 rounded-xl border border-slate-700 space-x-1">
          <button
            onClick={() => setViewMode('split')}
            className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
              viewMode === 'split' 
                ? 'bg-emerald-600 text-white shadow-md' 
                : 'text-slate-300 hover:text-white hover:bg-slate-700'
            }`}
          >
            <SplitSquareVertical className="w-3.5 h-3.5" />
            <span>Split Screen (Farmer + Officer)</span>
          </button>

          <button
            onClick={() => setViewMode('farmer')}
            className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
              viewMode === 'farmer' 
                ? 'bg-emerald-600 text-white shadow-md' 
                : 'text-slate-300 hover:text-white hover:bg-slate-700'
            }`}
          >
            <Smartphone className="w-3.5 h-3.5" />
            <span>Farmer Mobile App</span>
          </button>

          <button
            onClick={() => setViewMode('officer')}
            className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
              viewMode === 'officer' 
                ? 'bg-emerald-600 text-white shadow-md' 
                : 'text-slate-300 hover:text-white hover:bg-slate-700'
            }`}
          >
            <Building2 className="w-3.5 h-3.5" />
            <span>Officer Portal</span>
          </button>
        </div>
      </header>

      {/* Main Presentation View */}
      <main className="flex-1 overflow-hidden bg-slate-950 flex flex-col">
        {viewMode === 'split' && (
          <div className="flex-1 flex flex-col lg:flex-row h-full overflow-hidden">
            {/* Left Column: Farmer Android Phone Experience */}
            <div className="w-full lg:w-[460px] bg-slate-900 border-r border-slate-800 p-4 flex flex-col items-center justify-center overflow-y-auto">
              <div className="w-full flex items-center justify-between mb-2 px-3">
                <span className="text-xs font-bold uppercase tracking-wider text-emerald-400 flex items-center space-x-1.5">
                  <Smartphone className="w-4 h-4" />
                  <span>Farmer Mobile Edge App</span>
                </span>
                <span className="text-[10px] bg-slate-800 text-slate-300 px-2 py-0.5 rounded border border-slate-700">
                  Telugu / Hindi / English
                </span>
              </div>
              <FarmerMobileSimulator onTicketEscalated={handleTicketEscalated} />
            </div>

            {/* Right Column: Agricultural Extension Officer Operations Portal */}
            <div className="flex-1 bg-slate-50 flex flex-col overflow-y-auto text-slate-900">
              <Navbar user={user} onLogout={handleLogout} />
              <div className="p-4 sm:p-6 flex-1">
                {user ? (
                  <OfficerDashboard key={escalationTrigger} />
                ) : (
                  <OfficerLogin onLoginSuccess={handleLoginSuccess} />
                )}
              </div>
            </div>
          </div>
        )}

        {viewMode === 'farmer' && (
          <div className="flex-1 bg-slate-900 p-6 flex flex-col items-center justify-center overflow-y-auto">
            <div className="mb-4 text-center">
              <h2 className="text-xl font-bold text-white font-serif">బోల్ కిసాన్ • రైతు వాయిస్ అసిస్టెంట్</h2>
              <p className="text-xs text-emerald-300">Farmer Voice Assistant Android Simulation (Telugu / Hindi / English)</p>
            </div>
            <FarmerMobileSimulator onTicketEscalated={handleTicketEscalated} />
          </div>
        )}

        {viewMode === 'officer' && (
          <div className="flex-1 bg-slate-50 flex flex-col overflow-y-auto text-slate-900">
            <Navbar user={user} onLogout={handleLogout} />
            <div className="p-4 sm:p-6 flex-1">
              {user ? (
                <OfficerDashboard key={escalationTrigger} />
              ) : (
                <OfficerLogin onLoginSuccess={handleLoginSuccess} />
              )}
            </div>
          </div>
        )}
      </main>

      {/* Global Status Footer */}
      <footer className="bg-slate-950 border-t border-slate-800 px-4 py-2 flex items-center justify-between text-[11px] text-slate-400">
        <div className="flex items-center space-x-2">
          <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
          <span>Backend API: <b>http://127.0.0.1:8000</b> (FastAPI + Gemini 3.8 Flash + CIBRC Safety Gate)</span>
        </div>
        <span>BOL KISAN © 2026 • Verified Agricultural Intelligence & Extension Operations</span>
      </footer>
    </div>
  );
};

export default App;
