import React, { useState } from 'react';
import { api } from '../services/api';
import { ShieldCheck, Lock, Phone, KeyRound, ArrowRight } from 'lucide-react';

interface OfficerLoginProps {
  onLoginSuccess: () => void;
}

export const OfficerLogin: React.FC<OfficerLoginProps> = ({ onLoginSuccess }) => {
  const [officerCode, setOfficerCode] = useState('AGRI-TS-042');
  const [phoneNumber, setPhoneNumber] = useState('9123456789');
  const [otp, setOtp] = useState('654321');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      await api.login(officerCode, phoneNumber, otp);
      onLoginSuccess();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const loadDemo = () => {
    setOfficerCode('AGRI-TS-042');
    setPhoneNumber('9123456789');
    setOtp('654321');
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-forest-900 to-forest-950 flex items-center justify-center p-4">
      <div className="max-w-md w-full bg-white rounded-3xl shadow-2xl border border-forest-800 p-8">
        <div className="text-center space-y-2">
          <div className="inline-flex p-3 bg-forest-100 text-forest-800 rounded-2xl">
            <ShieldCheck className="w-10 h-10" />
          </div>
          <h1 className="text-2xl font-black text-slate-900 tracking-tight font-serif">BOL KISAN</h1>
          <p className="text-xs font-semibold uppercase text-harvest-600 tracking-wider">
            Agricultural Extension Officer Portal
          </p>
          <p className="text-xs text-slate-500">
            Secure governmental portal for Mandal & District Agricultural Officers (MAO/DAO)
          </p>
        </div>

        {error && (
          <div className="mt-4 p-3 bg-red-50 border border-red-200 text-red-700 text-xs rounded-xl font-medium">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-6 space-y-4">
          <div>
            <label className="block text-xs font-bold uppercase text-slate-600 mb-1">
              Officer Code
            </label>
            <div className="relative">
              <KeyRound className="w-4 h-4 text-slate-400 absolute left-3 top-3" />
              <input
                type="text"
                required
                value={officerCode}
                onChange={(e) => setOfficerCode(e.target.value)}
                placeholder="e.g. AGRI-TS-042"
                className="w-full pl-10 pr-4 py-2.5 border border-slate-300 rounded-xl text-sm focus:ring-2 focus:ring-forest-600 focus:border-forest-600 font-mono"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-bold uppercase text-slate-600 mb-1">
              Registered Phone Number
            </label>
            <div className="relative">
              <Phone className="w-4 h-4 text-slate-400 absolute left-3 top-3" />
              <input
                type="text"
                required
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value)}
                placeholder="10-digit mobile number"
                className="w-full pl-10 pr-4 py-2.5 border border-slate-300 rounded-xl text-sm focus:ring-2 focus:ring-forest-600 focus:border-forest-600 font-mono"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-bold uppercase text-slate-600 mb-1">
              Departmental Verification OTP
            </label>
            <div className="relative">
              <Lock className="w-4 h-4 text-slate-400 absolute left-3 top-3" />
              <input
                type="password"
                required
                value={otp}
                onChange={(e) => setOtp(e.target.value)}
                placeholder="6-digit OTP"
                className="w-full pl-10 pr-4 py-2.5 border border-slate-300 rounded-xl text-sm focus:ring-2 focus:ring-forest-600 focus:border-forest-600 font-mono"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-forest-700 hover:bg-forest-800 text-white font-bold py-3 rounded-xl shadow-lg transition-all flex items-center justify-center space-x-2 text-sm mt-2"
          >
            <span>{loading ? 'Verifying Departmental Credentials...' : 'Authenticate & Enter Triage Console'}</span>
            <ArrowRight className="w-4 h-4" />
          </button>
        </form>

        <div className="mt-6 pt-4 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
          <span>Demo Credentials:</span>
          <button
            onClick={loadDemo}
            className="text-forest-700 hover:underline font-bold"
          >
            Load Warangal MAO Account
          </button>
        </div>
      </div>
    </div>
  );
};
