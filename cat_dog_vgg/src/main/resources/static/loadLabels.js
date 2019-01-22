export async function loadImageNetLabels() {

return new Promise(async (res, rej) => {
    try {
        const response = await fetch('/imagenetClasses.json');
        const json = await response.json();
        res(json);
    } catch(e) {
        console.log(e);
    }
});

}