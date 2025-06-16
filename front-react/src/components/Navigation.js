import React, { useState, useRef, useEffect } from "react";
import { Link } from "react-router-dom";
import styles from "./Navigation.module.css";
import { FaUser, FaSignOutAlt, FaUserCircle } from "react-icons/fa";
import { IoMdArrowDropdown } from "react-icons/io";
import { useRoles } from "./shared/UseRoles";

export default function Navigation({ keycloak, realm }) {
  const user = keycloak?.tokenParsed?.preferred_username;
  const [showMenu, setShowMenu] = useState(false);
  const [profileImage, setProfileImage] = useState(null);
  const menuRef = useRef();
  const { isAdmin, isPsychologist, isPatient } = useRoles(keycloak);

  const logout = () => {
    keycloak.logout();
  };

  const toggleMenu = () => {
    setShowMenu(prev => !prev);
  };

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setShowMenu(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  useEffect(() => {
    const fetchProfileImage = async () => {
      try {
        const res = await fetch(`${process.env.REACT_APP_API_URL}/user/keycloak`, {
          headers: {
            Authorization: "Bearer " + keycloak.token,
            Tenant: realm
          },
        });
        const data = await res.json();
        if (data.profileImage) {
          setProfileImage(data.profileImage);
        }
      } catch (err) {
        console.error("Erro ao buscar imagem de perfil", err);
      }
    };

    fetchProfileImage();
  }, []);

  return (
      <nav className={styles.navbar}>
        <div className={styles.left}>
          <Link to="/" className={styles.logo}>Psicologia</Link>
        </div>

        <div className={styles.right} ref={menuRef}>
          <div className={styles.userMenu} onClick={toggleMenu}>
            {profileImage ? (
                <img
                    src={`data:image/jpeg;base64,${profileImage}`}
                    alt="Avatar"
                    className={styles.userImage}
                />
            ) : (
                <FaUserCircle className={styles.userIcon} />
            )}
            <span>{user}</span>
            <IoMdArrowDropdown className={styles.arrowIcon} />
          </div>
          {showMenu && (
              <div className={styles.dropdown}>
                <Link to="/perfil" className={styles.dropdownItem}>
                  <FaUser className={styles.icon} /> Ver Perfil
                </Link>
                <div className={`${styles.dropdownItem} ${styles.logoutItem}`} onClick={logout}>
                  <FaSignOutAlt className={styles.icon} /> Sair
                </div>
              </div>
          )}
        </div>
      </nav>
  );
}
