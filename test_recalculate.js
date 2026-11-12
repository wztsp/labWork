const http = require('http');

// Test recalculate API for a specific lot
const lotId = 18; // DEBUG-13S-LOT
const url = `http://localhost:8080/api/qc/records/recalculate?lotId=${lotId}`;

const options = {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  }
};

const req = http.request(url, options, (res) => {
  let data = '';
  res.on('data', chunk => data += chunk);
  res.on('end', () => {
    try {
      const result = JSON.parse(data);
      console.log('=== 失控计算 API 响应 ===');
      console.log('Code:', result.code);
      console.log('Message:', result.message);
      if (result.data) {
        console.log('Total:', result.data.total);
        console.log('Reject Count:', result.data.rejectCount);
        console.log('Updated Count:', result.data.updatedCount);
      }
    } catch (e) {
      console.log('Raw response:', data.substring(0, 500));
    }
  });
});

req.on('error', (err) => {
  console.log('Error:', err.message);
});

req.end();
