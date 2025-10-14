import React from 'react';
import { FaSun, FaMoon } from 'react-icons/fa';
import './Header.css';

const Header = ({ theme, onToggleTheme }) => {
  return (
    <>
      <div className="toggle-container">
        <FaSun />
        <label className="toggle-switch">
          <input type="checkbox" checked={theme === 'dark'} onChange={onToggleTheme} />
          <span className="slider round"></span>
        </label>
        <FaMoon />
      </div>
      <h1>Outlays Reminder</h1>
    </>
  );
};

export default Header;
