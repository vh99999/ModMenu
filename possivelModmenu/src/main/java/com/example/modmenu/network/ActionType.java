package com.example.modmenu.network;

public enum ActionType {
    CREATE_NETWORK(0),
    DELETE_NETWORK(1),
    ADD_PHYSICAL_NODE(2),
    REMOVE_NODE(3),
    ADD_UPDATE_RULE(4),
    REMOVE_RULE(5),
    TOGGLE_NETWORK_ACTIVE(7),
    ADD_VIRTUAL_NODE(9),
    UPDATE_NODE(11),
    RENAME_NETWORK(12),
    REQUEST_NODE_INVENTORY_PROBE(13),
    TEST_RULE(14),
    OPEN_MACHINE_GUI(15),
    TOGGLE_CONNECTIONS_VISIBILITY(16),
    TOGGLE_SIMULATION(17),
    SET_LINK_MODE(18),
    ADD_UPDATE_GROUP(19),
    REMOVE_GROUP(20),
    REQUEST_GROUP_INVENTORY_PROBE(21),
    ADD_UPDATE_TEMPLATE(22),
    REMOVE_TEMPLATE(23),
    APPLY_TEMPLATE(24),
    PASTE_NODE_CONFIG(25),
    SET_OVERFLOW_TARGET(26),
    SET_VIEWED_NETWORK(27),
    IMPORT_BLUEPRINT(30);

    private final int id;

    ActionType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static ActionType fromId(int id) {
        for (ActionType type : values()) {
            if (type.id == id) return type;
        }
        return null;
    }
}
