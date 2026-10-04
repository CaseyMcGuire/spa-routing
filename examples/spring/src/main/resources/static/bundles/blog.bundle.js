const root = document.getElementById("root");
const heading = document.createElement("h1");
heading.textContent = "Blog";

const description = document.createElement("p");
description.textContent = "Spring Boot serves this page using shared Kotlin route definitions.";

const currentLocation = document.createElement("p");
currentLocation.textContent = "Current route: " + window.location.pathname + window.location.search;

root.replaceChildren(heading, description, currentLocation);
