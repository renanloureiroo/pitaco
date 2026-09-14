const http = require('http');
const https = require('https');

async function fetchSurvey() {
  const url = 'https://pitaco.renanloureiro.me/api/applications';
  const response = await fetch(url);
  const apps = await response.json();
  const demoApp = apps.items.find(a => a.name === 'demo');
  if (!demoApp) {
    console.log('App demo not found');
    return;
  }
  
  const surveysResponse = await fetch(`https://pitaco.renanloureiro.me/api/applications/${demoApp.id}/surveys?page=0&size=100`);
  const surveys = await surveysResponse.json();
  const demoSurvey = surveys.items.find(s => s.name === 'Pesquisa de exemplo');
  
  if (!demoSurvey) {
    console.log('Survey not found');
    return;
  }
  
  const detailResponse = await fetch(`https://pitaco.renanloureiro.me/api/applications/${demoApp.id}/surveys/${demoSurvey.id}`);
  const detail = await detailResponse.json();
  console.log(JSON.stringify(detail, null, 2));
}

fetchSurvey().catch(console.error);
