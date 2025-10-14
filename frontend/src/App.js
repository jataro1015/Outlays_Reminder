import React, { useState } from 'react';
import './App.css';
import Header from './components/Header';
import { OutlayManager } from './components/OutlayManager';


function App() {
  const [theme, setTheme] = useState('dark'); // 'dark' or 'light'

  const toggleTheme = () => {
    setTheme(theme === 'dark' ? 'light' : 'dark');
  };

  return (
    <div className={`App ${theme}`}>
      <header className="App-header">
        <Header theme={theme} onToggleTheme={toggleTheme} />
        <OutlayManager />
      </header>
    </div>
  );
}

export default App;