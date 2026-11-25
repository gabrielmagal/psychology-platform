import { createTheme } from "@mui/material/styles";

/**
 * Faz os componentes do Material UI (campos de formulário, seletores de data, janelas)
 * seguirem a mesma identidade de theme.css, em vez do azul e das maiúsculas padrão.
 */
const muiTheme = createTheme({
    palette: {
        primary: { main: "#17736a", dark: "#145c56", light: "#45aa9d", contrastText: "#ffffff" },
        error: { main: "#c23434" },
        success: { main: "#177a4c" },
        warning: { main: "#a8690f" },
        text: { primary: "#212931", secondary: "#6a7783" },
        background: { default: "#f6f8f9", paper: "#ffffff" },
        divider: "#e0e5e9",
    },
    shape: { borderRadius: 6 },
    typography: {
        fontFamily: '"Inter", "Segoe UI", system-ui, -apple-system, Roboto, Arial, sans-serif',
        fontSize: 14,
        button: { textTransform: "none", fontWeight: 600 },
    },
    components: {
        MuiTextField: { defaultProps: { size: "small" } },
        MuiButton: {
            defaultProps: { disableElevation: true },
            styleOverrides: { root: { borderRadius: 6, minHeight: 38 } },
        },
        MuiOutlinedInput: {
            styleOverrides: {
                root: { backgroundColor: "#ffffff" },
                notchedOutline: { borderColor: "#c8d0d7" },
            },
        },
        MuiDialog: { styleOverrides: { paper: { borderRadius: 14 } } },
        MuiDialogTitle: { styleOverrides: { root: { fontWeight: 700, fontSize: "1.15rem" } } },
    },
});

export default muiTheme;
