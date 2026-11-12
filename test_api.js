const http = require('http');

// Test 1: Basic API call
const url = 'http://localhost:8080/api/qc/records/page?current=1&size=10';

http.get(url, (res) => {
  let data = '';
  res.on('data', chunk => data += chunk);
  res.on('end', () => {
    try {
      const result = JSON.parse(data);
      console.log('=== API Response ===');
      console.log('Code:', result.code);
      console.log('Message:', result.message);
      if (result.data) {
        console.log('Total:', result.data.total);
        console.log('Records count:', result.data.records ? result.data.records.length : 0);
        if (result.data.records && result.data.records.length > 0) {
          console.log('\nFirst record:');
          console.log(JSON.stringify(result.data.records[0], null, 2));
        }
      }
    } catch (e) {
      console.log('Raw response:', data.substring(0, 500));
    }
  });
}).on('error', (err) => {
  console.log('Error:', err.message);
});

// Test 2: Check if login is required
setTimeout(() => {
  http.get('http://localhost:8080/api/auth/login', (res) => {
    console.log('\n=== Auth endpoint status ===');
    console.log('Status:', res.statusCode);
  }).on('error', (err) => {
    console.log('Auth error:', err.message);
  });
}, 500);
