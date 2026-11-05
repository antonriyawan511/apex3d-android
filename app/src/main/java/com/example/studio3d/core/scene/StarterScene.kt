package com.example.studio3d.core.scene

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import com.example.studio3d.core.math.Vector3
import java.util.UUID

object StarterScene {

    fun populateDefaultScene(graph: SceneGraph) {
        graph.clear()

        val defaultCube = SceneNode(
            id = "default_cube",
            name = "Cube",
            position = Vector3(0f, 0.5f, 0f),
            scale = Vector3(1f, 1f, 1f),
            meshType = "cube",
            material = Material.studioClay()
        )
        defaultCube.ensureMesh()
        graph.addNode(defaultCube)

        // Select the cube by default
        graph.selectedNodeId = defaultCube.id
    }

    fun populateAutomotiveScene(graph: SceneGraph) {
        graph.clear()

        // 1. Root Car Assembly Group
        val carGroup = SceneNode(
            id = "car_assembly",
            name = "Apex GT Concept",
            position = Vector3(0f, 0.45f, 0f),
            meshType = "group"
        )
        graph.addNode(carGroup)

        // 2. Main Aerodynamic Chassis Body
        val bodyNode = SceneNode(
            id = "chassis_body",
            name = "Aero Monocoque Chassis",
            position = Vector3(0f, 0.28f, 0f),
            scale = Vector3(1f, 1f, 1f),
            meshType = "car_body",
            material = Material.automotiveCyberCyan()
        )
        graph.addNode(bodyNode, carGroup.id)

        // 3. Cockpit Canopy (Tinted Glass)
        val cockpitNode = SceneNode(
            id = "cockpit_canopy",
            name = "Cockpit Canopy",
            position = Vector3(0f, 0.65f, -0.2f),
            scale = Vector3(1.1f, 0.48f, 1.4f),
            meshType = "wedge",
            rotation = Vector3(0f, 180f, 0f),
            material = Material.tintedGlass()
        )
        graph.addNode(cockpitNode, carGroup.id)

        // 4. Wheels (Front-Left, Front-Right, Rear-Left, Rear-Right)
        val wheelFL = SceneNode(
            id = "wheel_fl",
            name = "Wheel Front-Left",
            position = Vector3(-0.95f, 0f, 1.15f),
            rotation = Vector3(0f, 180f, 0f),
            scale = Vector3(1f, 1f, 1f),
            meshType = "wheel",
            material = Material.matteBlackRubber()
        )
        graph.addNode(wheelFL, carGroup.id)

        val wheelFR = SceneNode(
            id = "wheel_fr",
            name = "Wheel Front-Right",
            position = Vector3(0.95f, 0f, 1.15f),
            rotation = Vector3(0f, 0f, 0f),
            scale = Vector3(1f, 1f, 1f),
            meshType = "wheel",
            material = Material.matteBlackRubber()
        )
        graph.addNode(wheelFR, carGroup.id)

        val wheelRL = SceneNode(
            id = "wheel_rl",
            name = "Wheel Rear-Left",
            position = Vector3(-0.98f, 0.04f, -1.25f),
            rotation = Vector3(0f, 180f, 0f),
            scale = Vector3(1.1f, 1.1f, 1.1f),
            meshType = "wheel",
            material = Material.matteBlackRubber()
        )
        graph.addNode(wheelRL, carGroup.id)

        val wheelRR = SceneNode(
            id = "wheel_rr",
            name = "Wheel Rear-Right",
            position = Vector3(0.98f, 0.04f, -1.25f),
            rotation = Vector3(0f, 0f, 0f),
            scale = Vector3(1.1f, 1.1f, 1.1f),
            meshType = "wheel",
            material = Material.matteBlackRubber()
        )
        graph.addNode(wheelRR, carGroup.id)

        // 5. Rear High-Downforce Spoiler
        val spoilerWing = SceneNode(
            id = "rear_spoiler",
            name = "Carbon Rear Wing",
            position = Vector3(0f, 0.85f, -1.65f),
            scale = Vector3(1.7f, 0.08f, 0.35f),
            meshType = "cube",
            material = Material(0.12f, 0.12f, 0.14f, 1f, 0.3f, 0.4f, 0.8f)
        )
        graph.addNode(spoilerWing, carGroup.id)

        // 6. Front Aerodynamic Splitter
        val frontSplitter = SceneNode(
            id = "front_splitter",
            name = "Aero Front Splitter",
            position = Vector3(0f, 0.05f, 1.75f),
            scale = Vector3(1.5f, 0.06f, 0.4f),
            meshType = "wedge",
            material = Material(0.15f, 0.15f, 0.18f, 1f, 0.2f, 0.5f, 0.6f)
        )
        graph.addNode(frontSplitter, carGroup.id)

        // Select the body by default
        graph.selectedNodeId = bodyNode.id
    }
}
