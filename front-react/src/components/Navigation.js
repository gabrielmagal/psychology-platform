import React, { useState, useRef, useEffect } from "react";
import { Link } from "react-router-dom";
import styles from "./Navigation.module.css";
import { FaUserCircle } from "react-icons/fa";
import { IoMdArrowDropdown } from "react-icons/io";

export default function Navigation({ keycloak }) {
  const user = keycloak?.tokenParsed?.preferred_username;
  const [showMenu, setShowMenu] = useState(false);
  const menuRef = useRef();

  const logout = () => {
    keycloak.logout();
  };

  const toggleMenu = () => {
    setShowMenu(prev => !prev);
  };

  // Fecha menu ao clicar fora
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setShowMenu(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <nav className={styles.navbar}>
      <div className={styles.left}>
        <Link to="/" className={styles.logo}>Psicologia</Link>
        <Link to="/usuarios">Usuários</Link>
        <Link to="/sessions-package">Pacote de Sessões</Link>
        <Link to="/sessions">Sessões</Link>
        <Link to="/payments">Pagamentos</Link>
        <Link to="/annotations">Anotações</Link>
      </div>

      <div className={styles.right} ref={menuRef}>
        <div className={styles.userMenu} onClick={toggleMenu}>
          <FaUserCircle className={styles.userIcon} />
          <span>{user}</span>
          <IoMdArrowDropdown className={styles.arrowIcon} />
        </div>
        {showMenu && (
          <div className={styles.dropdown}>
            <button onClick={logout}>Sair</button>
          </div>
        )}
      </div>
    </nav>
  );
}
