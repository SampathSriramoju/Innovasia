import React from 'react';
import { OfficerUser } from '../types';
import { ShieldCheck, LogOut, MapPin } from 'lucide-react';

interface NavbarProps {
  user: OfficerUser | null;
  onLogout: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({ user, onLogout }) => {
  return (
    <header className="bg-forest-900 text-white shadow-md border-b border-forest-800">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        <div className="flex items-center space-x-3">
          <div className="bg-forest-700 p-2 rounded-lg text-white">
            <ShieldCheck className="w-6 h-6" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <span className="text-xl font-bold tracking-tight font-serif">BOL KISAN</span>
              <span className="bg-harvest-500 text-white text-xs px-2 py-0.5 rounded font-medium">OFFICER DESK</span>
            </div>
            <p className="text-xs text-forest-200">Regional Agricultural Extension & Triage Console</p>
          </div>
        </div>

        {user && (
          <div className="flex items-center space-x-4">
            <div className="text-right hidden sm:block">
              <p className="text-sm font-semibold">{user.designation}</p>
              <div className="flex items-center space-x-1 text-xs text-forest-200 justify-end">
                <MapPin className="w-3 h-3 text-harvest-500" />
                <span>{user.assigned_district} ({user.assigned_mandals.join(', ')})</span>
              </div>
            </div>

            <button
              onClick={onLogout}
              className="flex items-center space-x-1 bg-forest-800 hover:bg-forest-700 text-forest-100 px-3 py-1.5 rounded-lg text-sm transition-colors border border-forest-700"
            >
              <LogOut className="w-4 h-4" />
              <span>Logout</span>
            </button>
          </div>
        )}
      </div>
    </header>
  );
};
